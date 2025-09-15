package edu.unifor.clysman.chat.model;

import java.util.List;
import java.util.UUID;

public class Message {

    private String id;
    private String type; // HELLO, CHAT, PEERSHARE
    private String fromId;
    private String fromName;
    private String text;
    private long timestamp;
    private List<PeerInfo> peers; // para HELLO/PEERSHARE

    public static Message chat(String fromId, String fromName, String text) {
        Message m = new Message();
        m.id = UUID.randomUUID().toString();
        m.type = "CHAT";
        m.fromId = fromId;
        m.fromName = fromName;
        m.text = text;
        m.timestamp = System.currentTimeMillis();
        return m;
    }

    public static Message hello(String fromId, String fromName, int port, List<PeerInfo> peers) {
        Message m = new Message();
        m.id = UUID.randomUUID().toString();
        m.type = "HELLO";
        m.fromId = fromId;
        m.fromName = fromName;
        m.timestamp = System.currentTimeMillis();
        m.peers = peers;
        return m;
    }

    public static Message peerShare(String fromId, String fromName, List<PeerInfo> peers) {
        Message m = new Message();
        m.id = UUID.randomUUID().toString();
        m.type = "PEERSHARE";
        m.fromId = fromId;
        m.fromName = fromName;
        m.timestamp = System.currentTimeMillis();
        m.peers = peers;
        return m;
    }

    public String getId() { return id; }
    public String getType() { return type; }
    public String getFromId() { return fromId; }
    public String getFromName() { return fromName; }
    public String getText() { return text; }
    public long getTimestamp() { return timestamp; }
    public List<PeerInfo> getPeers() { return peers; }

    public void setId(String id) { this.id = id; }
    public void setType(String type) { this.type = type; }
    public void setFromId(String fromId) { this.fromId = fromId; }
    public void setFromName(String fromName) { this.fromName = fromName; }
    public void setText(String text) { this.text = text; }
    public void setTimestamp(long timestamp) { this.timestamp = timestamp; }
    public void setPeers(List<PeerInfo> peers) { this.peers = peers; }
}