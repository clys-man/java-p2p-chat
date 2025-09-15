package edu.unifor.clysman.chat.net;

import edu.unifor.clysman.chat.model.Message;
import edu.unifor.clysman.chat.model.PeerInfo;
import edu.unifor.clysman.chat.util.Json;
import com.google.gson.Gson;

import java.io.*;
import java.net.Socket;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicBoolean;

class ConnectionHandler {

    private final Socket socket;
    private final PeerNode node;
    private volatile PeerInfo peerInfo;

    private Thread readerThread;
    private Thread writerThread;
    private final BlockingQueue<String> outQueue = new LinkedBlockingQueue<>();
    private final AtomicBoolean running = new AtomicBoolean(false);
    private final Gson gson = Json.get();
    private volatile BufferedReader in;
    private volatile BufferedWriter out;

    ConnectionHandler(Socket socket, PeerNode node) {
        this.socket = socket;
        this.node = node;
    }

    void start() {
        if (!running.compareAndSet(false, true)) return;
        try {
            in = new BufferedReader(new InputStreamReader(socket.getInputStream(), "UTF-8"));
            out = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream(), "UTF-8"));
        } catch (IOException e) {
            stop();
            return;
        }

        readerThread = new Thread(this::readLoop, "reader-" + getRemoteKey());
        writerThread = new Thread(this::writeLoop, "writer-" + getRemoteKey());
        readerThread.setDaemon(true);
        writerThread.setDaemon(true);
        readerThread.start();
        writerThread.start();

        node.onHandlerStarted(this);
    }

    void stop() {
        if (!running.compareAndSet(true, false)) return;
        try { socket.close(); } catch (IOException ignored) {}
        if (readerThread != null) readerThread.interrupt();
        if (writerThread != null) writerThread.interrupt();
        node.onHandlerStopped(this);
    }

    void send(Message m) {
        if (!running.get()) return;
        try {
            outQueue.offer(gson.toJson(m));
        } catch (Exception ignored) {}
    }

    private void readLoop() {
        try {
            String line;
            while (running.get() && (line = in.readLine()) != null) {
                node.onLineReceived(this, line);
            }
        } catch (IOException ignored) {
        } finally {
            stop();
        }
    }

    private void writeLoop() {
        try {
            while (running.get()) {
                String line = outQueue.take();
                out.write(line);
                out.write("\n");
                out.flush();
            }
        } catch (Exception ignored) {
        } finally {
            stop();
        }
    }

    String getRemoteKey() {
        return socket.getRemoteSocketAddress() != null ? socket.getRemoteSocketAddress().toString() : "unknown";
    }

    String getRemoteHost() {
        return socket.getInetAddress() != null ? socket.getInetAddress().getHostAddress() : "unknown";
    }

    int getRemotePort() {
        return socket.getPort();
    }

    public PeerInfo getPeerInfo() {
        return peerInfo;
    }

    public void setPeerInfo(PeerInfo peerInfo) {
        this.peerInfo = peerInfo;
    }
}