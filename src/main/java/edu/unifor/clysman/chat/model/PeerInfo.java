package edu.unifor.clysman.chat.model;

public class PeerInfo {
    private String id;
    private String name;
    private String host;
    private int port;
    private long lastSeen;

    public String getId() { return id; }
    public String getName() { return name; }
    public String getHost() { return host; }
    public int getPort() { return port; }
    public long getLastSeen() { return lastSeen; }

    public void setId(String id) { this.id = id; }
    public void setName(String name) { this.name = name; }
    public void setHost(String host) { this.host = host; }
    public void setPort(int port) { this.port = port; }
    public void setLastSeen(long lastSeen) { this.lastSeen = lastSeen; }
}