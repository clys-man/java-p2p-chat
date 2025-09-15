package edu.unifor.clysman.chat.net;

import edu.unifor.clysman.chat.model.Message;
import edu.unifor.clysman.chat.model.PeerInfo;
import edu.unifor.clysman.chat.util.Json;
import edu.unifor.clysman.chat.util.LruSet;
import com.google.gson.Gson;

import java.io.IOException;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

public class PeerNode {

    public interface UiCallbacks {
        void onStatus(String msg);
        void onPeerConnected(PeerInfo peer);
        void onPeerDisconnected(PeerInfo peer);
        void onMessageReceived(Message m);
        void onPeerDiscovered(String host, int port, String name, String id);
    }

    private final String myId = UUID.randomUUID().toString();
    private volatile String myName;
    private final int configuredPort;
    private volatile int listenPort;
    private ServerSocket serverSocket;
    private Thread acceptThread;

    private final Map<String, ConnectionHandler> connectionsByPeerId = new ConcurrentHashMap<>();
    private final Map<String, PeerInfo> peersById = new ConcurrentHashMap<>();
    private final Map<String, ConnectionHandler> pendingByRemote = new ConcurrentHashMap<>();

    private final ExecutorService ioPool = Executors.newCachedThreadPool();
    private final AtomicBoolean running = new AtomicBoolean(false);

    private UiCallbacks ui;
    private final Gson gson = Json.get();
    private DiscoveryService discovery;

    private final LruSet<String> seenMessageIds = new LruSet<>(5000);

    public PeerNode(String myName, int port) {
        this.myName = Objects.requireNonNull(myName);
        this.configuredPort = port;
    }

    public void setUiCallbacks(UiCallbacks ui) {
        this.ui = ui;
    }

    public String getMyId() {
        return myId;
    }

    public String getMyName() {
        return myName;
    }

    public int getListenPort() {
        return listenPort;
    }

    public void start() throws IOException {
        if (!running.compareAndSet(false, true)) return;

        // Abrir servidor; caso porta ocupada, tenta próxima
        int port = configuredPort;
        ServerSocket ss = null;
        while (port < configuredPort + 50) {
            try {
                ss = new ServerSocket();
                ss.bind(new InetSocketAddress("0.0.0.0", port));
                break;
            } catch (IOException e) {
                port++;
            }
        }
        if (ss == null) {
            running.set(false);
            throw new IOException("Não foi possível abrir uma porta de escuta.");
        }
        serverSocket = ss;
        listenPort = port;

        acceptThread = new Thread(this::acceptLoop, "accept-loop");
        acceptThread.setDaemon(true);
        acceptThread.start();

        // Inicia descoberta via multicast
        discovery = new DiscoveryService(myId, myName, listenPort, (host, p, name, id) -> {
            if (ui != null) ui.onPeerDiscovered(host, p, name, id);
        });
        discovery.start();

        if (ui != null) ui.onStatus("Escutando em " + listenPort + " (ID: " + myId + ")");
    }

    private void acceptLoop() {
        try {
            while (running.get()) {
                Socket socket = serverSocket.accept();
                socket.setTcpNoDelay(true);
                String remoteKey = remoteKey(socket);
                ConnectionHandler handler = new ConnectionHandler(socket, this);
                pendingByRemote.put(remoteKey, handler);
                ioPool.submit(handler::start);
            }
        } catch (IOException e) {
            if (running.get() && ui != null) ui.onStatus("Loop de aceitação encerrado: " + e.getMessage());
        }
    }

    public void connectAsync(String host, int port) {
        ioPool.submit(() -> {
            try {
                Socket socket = new Socket();
                socket.connect(new InetSocketAddress(host, port), 4000);
                socket.setTcpNoDelay(true);
                ConnectionHandler handler = new ConnectionHandler(socket, this);
                pendingByRemote.put(remoteKey(socket), handler);
                handler.start();

                // Envia HELLO
                sendHello(handler);
                if (ui != null) ui.onStatus("Conectado a " + host + ":" + port);
            } catch (IOException e) {
                if (ui != null) ui.onStatus("Falha ao conectar: " + e.getMessage());
            }
        });
    }

    private void sendHello(ConnectionHandler handler) {
        Message hello = Message.hello(myId, myName, listenPort, new ArrayList<>(peersById.values()));
        handler.send(hello);
    }

    void onHandlerStarted(ConnectionHandler handler) {
        // Quando handler inicia, enviamos HELLO também (caso seja inbound)
        sendHello(handler);
    }

    void onHandlerStopped(ConnectionHandler handler) {
        PeerInfo p = handler.getPeerInfo();
        if (p != null) {
            connectionsByPeerId.remove(p.getId());
            if (ui != null) ui.onPeerDisconnected(p);
        }
        pendingByRemote.remove(handler.getRemoteKey());
    }

    void onLineReceived(ConnectionHandler handler, String line) {
        try {
            Message msg = gson.fromJson(line, Message.class);
            if (msg == null || msg.getType() == null) return;

            switch (msg.getType()) {
                case "HELLO":
                    handleHello(handler, msg);
                    break;
                case "CHAT":
                    handleChat(handler, msg);
                    break;
                case "PEERSHARE":
                    handlePeerShare(handler, msg);
                    break;
                default:
                    // ignorar tipos desconhecidos
            }
        } catch (Exception e) {
            if (ui != null) ui.onStatus("Falha ao processar mensagem: " + e.getMessage());
        }
    }

    private void handleHello(ConnectionHandler handler, Message m) {
        if (m.getFromId() == null) return;

        // Atualiza info do peer no handler e mapas
        PeerInfo info = new PeerInfo();
        info.setId(m.getFromId());
        info.setName(m.getFromName() != null ? m.getFromName() : "peer");
        info.setHost(handler.getRemoteHost());
        info.setPort(handler.getRemotePort());
        info.setLastSeen(System.currentTimeMillis());

        handler.setPeerInfo(info);
        connectionsByPeerId.put(info.getId(), handler);
        peersById.put(info.getId(), info);

        if (ui != null) ui.onPeerConnected(info);

        // Se HELLO trouxe peers conhecidos, compartilha/atualiza
        if (m.getPeers() != null && !m.getPeers().isEmpty()) {
            for (PeerInfo pi : m.getPeers()) {
                if (pi == null || pi.getId() == null) continue;
                if (pi.getId().equals(myId)) continue;
                peersById.put(pi.getId(), pi);
            }
            // Compartilha minha visão também
            sharePeers(handler);
        }
    }

    private void sharePeers(ConnectionHandler to) {
        Message share = Message.peerShare(myId, myName, new ArrayList<>(peersById.values()));
        if (to != null) {
            to.send(share);
        } else {
            broadcast(share, null);
        }
    }

    private void handleChat(ConnectionHandler handler, Message m) {
        String id = m.getId();
        if (id == null) return;

        // Deduplica
        if (!seenMessageIds.add(id)) {
            return; // já recebida
        }

        // Atualiza lastSeen do remetente
        PeerInfo sender = connectionsByPeerId.getOrDefault(m.getFromId(), handler).getPeerInfo();
        if (sender != null) {
            sender.setLastSeen(System.currentTimeMillis());
            peersById.put(sender.getId(), sender);
        }

        // Entrega à UI
        if (ui != null) ui.onMessageReceived(m);

        // Rebroadcast para todos, exceto por onde chegou
        broadcast(m, handler);
    }

    private void handlePeerShare(ConnectionHandler handler, Message m) {
        if (m.getPeers() == null) return;
        int attempts = 0;
        for (PeerInfo pi : m.getPeers()) {
            if (pi == null || pi.getId() == null) continue;
            if (pi.getId().equals(myId)) continue;
            peersById.put(pi.getId(), pi);

            // Estratégia simples: tentar conectar até 3 peers novos
            if (!connectionsByPeerId.containsKey(pi.getId()) && attempts < 3) {
                attempts++;
                connectAsync(pi.getHost(), pi.getPort());
            }
        }
    }

    public void broadcastChat(String text) {
        Message m = Message.chat(myId, myName, text);
        // Marca como visto localmente e entrega na UI local
        seenMessageIds.add(m.getId());
        if (ui != null) ui.onMessageReceived(m);
        // Envia para todos
        broadcast(m, null);
    }

    private void broadcast(Message m, ConnectionHandler except) {
        for (ConnectionHandler ch : new ArrayList<>(connectionsByPeerId.values())) {
            if (except != null && ch == except) continue;
            ch.send(m);
        }
    }

    public void shutdown() {
        if (!running.compareAndSet(true, false)) return;

        if (discovery != null) {
            discovery.shutdown();
            discovery = null;
        }

        try {
            if (serverSocket != null) serverSocket.close();
        } catch (IOException ignored) {}

        for (ConnectionHandler ch : new ArrayList<>(connectionsByPeerId.values())) {
            ch.stop();
        }
        for (ConnectionHandler ch : new ArrayList<>(pendingByRemote.values())) {
            ch.stop();
        }
        ioPool.shutdownNow();
        if (ui != null) ui.onStatus("Encerrado.");
    }

    private static String remoteKey(Socket s) {
        SocketAddress ra = s.getRemoteSocketAddress();
        return ra != null ? ra.toString() : UUID.randomUUID().toString();
    }

    private static String remoteKey(String host, int port) {
        return host + ":" + port;
    }
}