package edu.unifor.clysman.chat.net;

import com.google.gson.Gson;
import com.google.gson.annotations.SerializedName;

import java.io.IOException;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

public class DiscoveryService {

    public interface Listener {
        void onDiscovered(String host, int port, String name, String id);
    }

    private static final String GROUP = "230.0.0.1";
    private static final int PORT = 4446;

    private final String myId;
    private volatile String myName;
    private final int myPort;

    private final Listener listener;
    private final Gson gson = new Gson();

    private final AtomicBoolean running = new AtomicBoolean(false);
    private MulticastSocket socket;
    private InetAddress group;
    private Thread recvThread;
    private ScheduledExecutorService senderScheduler;

    public DiscoveryService(String myId, String myName, int myPort, Listener listener) {
        this.myId = Objects.requireNonNull(myId);
        this.myName = Objects.requireNonNull(myName);
        this.myPort = myPort;
        this.listener = listener;
    }

    public void start() throws IOException {
        if (!running.compareAndSet(false, true)) return;

        socket = new MulticastSocket(PORT);
        socket.setReuseAddress(true);
        socket.setTimeToLive(1);
        group = InetAddress.getByName(GROUP);
        socket.joinGroup(group);

        recvThread = new Thread(this::recvLoop, "discovery-recv");
        recvThread.setDaemon(true);
        recvThread.start();

        senderScheduler = Executors.newSingleThreadScheduledExecutor();
        senderScheduler.scheduleAtFixedRate(this::sendAnnounce, 0, 3, TimeUnit.SECONDS);
    }

    public void shutdown() {
        if (!running.compareAndSet(true, false)) return;
        if (senderScheduler != null) {
            senderScheduler.shutdownNow();
            senderScheduler = null;
        }
        try {
            if (socket != null) {
                socket.leaveGroup(group);
                socket.close();
            }
        } catch (IOException ignored) {}
        if (recvThread != null) recvThread.interrupt();
    }

    private void sendAnnounce() {
        try {
            Announce a = new Announce();
            a.type = "DISCOVERY";
            a.id = myId;
            a.name = myName;
            a.port = myPort;

            byte[] buf = gson.toJson(a).getBytes(StandardCharsets.UTF_8);
            DatagramPacket packet = new DatagramPacket(buf, buf.length, group, PORT);
            socket.send(packet);
        } catch (IOException ignored) {}
    }

    private void recvLoop() {
        byte[] buf = new byte[1024];
        while (running.get()) {
            try {
                DatagramPacket packet = new DatagramPacket(buf, buf.length);
                socket.receive(packet);
                String json = new String(packet.getData(), packet.getOffset(), packet.getLength(), StandardCharsets.UTF_8);
                Announce a = gson.fromJson(json, Announce.class);
                if (a == null || !"DISCOVERY".equals(a.type)) continue;
                if (myId.equals(a.id)) continue; // ignora a si mesmo
                String host = packet.getAddress().getHostAddress();
                if (listener != null) listener.onDiscovered(host, a.port, a.name, a.id);
            } catch (IOException e) {
                if (running.get()) break;
            }
        }
    }

    static class Announce {
        @SerializedName("type")
        String type;
        @SerializedName("id")
        String id;
        @SerializedName("name")
        String name;
        @SerializedName("port")
        int port;
    }
}