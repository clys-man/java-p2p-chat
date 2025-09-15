package edu.unifor.clysman.chat.gui;

import edu.unifor.clysman.chat.model.Message;
import edu.unifor.clysman.chat.model.PeerInfo;
import edu.unifor.clysman.chat.net.PeerNode;
import edu.unifor.clysman.chat.service.MessageHistory;

import javax.swing.*;
import javax.swing.border.TitledBorder;
import java.awt.*;
import java.awt.event.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class ChatWindow extends JFrame {

    private final JTextArea chatArea = new JTextArea();
    private final JTextField inputField = new JTextField();
    private final JButton sendButton = new JButton("Enviar (Broadcast)");
    private final JTextField nameField = new JTextField("Usuario-" + new Random().nextInt(1000));
    private final JTextField listenPortField = new JTextField(String.valueOf(5000 + new Random().nextInt(1000)));
    private final JButton startButton = new JButton("Iniciar");
    private final JButton stopButton = new JButton("Parar");

    private final JTextField connectHostField = new JTextField("127.0.0.1");
    private final JTextField connectPortField = new JTextField("5000");
    private final JButton connectButton = new JButton("Conectar");

    private final DefaultListModel<String> connectedModel = new DefaultListModel<>();
    private final JList<String> connectedList = new JList<>(connectedModel);

    private final DefaultListModel<String> discoveredModel = new DefaultListModel<>();
    private final JList<String> discoveredList = new JList<>(discoveredModel);
    private final JButton connectDiscoveredButton = new JButton("Conectar Selecionado");

    private PeerNode node;
    private MessageHistory history;

    private final Map<String, PeerInfo> connectedByKey = new ConcurrentHashMap<>();

    public ChatWindow() {
        super("P2P Chat - Java (Swing)");
        setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);
        setSize(1000, 650);
        setLocationRelativeTo(null);

        setLayout(new BorderLayout(8, 8));

        // Painel superior (configuração)
        JPanel topPanel = new JPanel(new GridBagLayout());
        topPanel.setBorder(new TitledBorder("Configuração"));
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(4,4,4,4);
        gbc.gridy = 0; gbc.gridx = 0; gbc.anchor = GridBagConstraints.WEST;

        topPanel.add(new JLabel("Nome:"), gbc);
        gbc.gridx++;
        nameField.setColumns(16);
        topPanel.add(nameField, gbc);

        gbc.gridx++;
        topPanel.add(new JLabel("Porta de escuta:"), gbc);
        gbc.gridx++;
        listenPortField.setColumns(6);
        topPanel.add(listenPortField, gbc);

        gbc.gridx++;
        topPanel.add(startButton, gbc);
        gbc.gridx++;
        stopButton.setEnabled(false);
        topPanel.add(stopButton, gbc);

        gbc.gridy++; gbc.gridx = 0;
        topPanel.add(new JLabel("Conectar em (host:porta):"), gbc);
        gbc.gridx++;
        connectHostField.setColumns(14);
        topPanel.add(connectHostField, gbc);
        gbc.gridx++;
        connectPortField.setColumns(6);
        topPanel.add(connectPortField, gbc);
        gbc.gridx++;
        topPanel.add(connectButton, gbc);

        add(topPanel, BorderLayout.NORTH);

        // Painel central (chat e listas)
        JSplitPane split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT);
        split.setResizeWeight(0.75);

        // Área de chat
        JPanel chatPanel = new JPanel(new BorderLayout(6,6));
        chatPanel.setBorder(new TitledBorder("Chat"));
        chatArea.setEditable(false);
        chatArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 13));
        JScrollPane chatScroll = new JScrollPane(chatArea);
        chatPanel.add(chatScroll, BorderLayout.CENTER);

        JPanel inputPanel = new JPanel(new BorderLayout(6,6));
        inputPanel.add(inputField, BorderLayout.CENTER);
        inputPanel.add(sendButton, BorderLayout.EAST);
        chatPanel.add(inputPanel, BorderLayout.SOUTH);

        split.setLeftComponent(chatPanel);

        // Painel lateral (peers)
        JPanel sidePanel = new JPanel(new GridLayout(2,1,6,6));

        JPanel connectedPanel = new JPanel(new BorderLayout());
        connectedPanel.setBorder(new TitledBorder("Conectados"));
        connectedList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        connectedPanel.add(new JScrollPane(connectedList), BorderLayout.CENTER);
        sidePanel.add(connectedPanel);

        JPanel discoveredPanel = new JPanel(new BorderLayout());
        discoveredPanel.setBorder(new TitledBorder("Descobertos (Multicast LAN)"));
        discoveredPanel.add(new JScrollPane(discoveredList), BorderLayout.CENTER);
        discoveredPanel.add(connectDiscoveredButton, BorderLayout.SOUTH);
        sidePanel.add(discoveredPanel);

        split.setRightComponent(sidePanel);

        add(split, BorderLayout.CENTER);

        // Eventos
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                performShutdown();
                dispose();
                System.exit(0);
            }
        });

        startButton.addActionListener(e -> startNode());
        stopButton.addActionListener(e -> stopNode());

        connectButton.addActionListener(e -> connectToManual());
        connectDiscoveredButton.addActionListener(e -> connectToDiscovered());

        sendButton.addActionListener(e -> sendChat());
        inputField.addActionListener(e -> sendChat());
    }

    private void startNode() {
        try {
            String name = nameField.getText().trim();
            int port = Integer.parseInt(listenPortField.getText().trim());
            if (name.isEmpty()) throw new IllegalArgumentException("Nome não pode ser vazio.");
            node = new PeerNode(name, port);
            node.setUiCallbacks(new PeerNode.UiCallbacks() {
                @Override
                public void onStatus(String msg) {
                    appendSystem(msg);
                }
                @Override
                public void onPeerConnected(PeerInfo peer) {
                    SwingUtilities.invokeLater(() -> {
                        String key = key(peer);
                        connectedByKey.put(key, peer);
                        if (!containsValue(connectedModel, key)) connectedModel.addElement(key);
                    });
                }
                @Override
                public void onPeerDisconnected(PeerInfo peer) {
                    SwingUtilities.invokeLater(() -> {
                        String key = key(peer);
                        connectedByKey.remove(key);
                        connectedModel.removeElement(key);
                    });
                }
                @Override
                public void onMessageReceived(Message m) {
                    SwingUtilities.invokeLater(() -> {
                        String line = String.format("[%tT] %s: %s", new Date(m.getTimestamp()), m.getFromName(), m.getText());
                        chatArea.append(line + "\n");
                        if (history != null) history.append(line);
                        chatArea.setCaretPosition(chatArea.getDocument().getLength());
                    });
                }
                @Override
                public void onPeerDiscovered(String host, int port, String name, String id) {
                    SwingUtilities.invokeLater(() -> {
                        String entry = host + ":" + port + " (" + name + ")";
                        if (!containsValue(discoveredModel, entry)) discoveredModel.addElement(entry);
                    });
                }
            });
            node.start();

            history = new MessageHistory();
            history.start(name, node.getListenPort());

            startButton.setEnabled(false);
            stopButton.setEnabled(true);
            nameField.setEnabled(false);
            listenPortField.setEnabled(false);
            appendSystem("Nó iniciado. Aguardando conexões em porta " + node.getListenPort());
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, "Falha ao iniciar: " + ex.getMessage(), "Erro", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void stopNode() {
        try {
            if (node != null) {
                node.shutdown();
                node = null;
            }
            if (history != null) {
                history.close();
                history = null;
            }
            connectedModel.clear();
            discoveredModel.clear();
            connectedByKey.clear();
            startButton.setEnabled(true);
            stopButton.setEnabled(false);
            nameField.setEnabled(true);
            listenPortField.setEnabled(true);
            appendSystem("Nó parado.");
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, "Falha ao parar: " + ex.getMessage(), "Erro", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void connectToManual() {
        if (node == null) {
            JOptionPane.showMessageDialog(this, "Inicie o nó antes de conectar.", "Atenção", JOptionPane.WARNING_MESSAGE);
            return;
        }
        String host = connectHostField.getText().trim();
        int port = Integer.parseInt(connectPortField.getText().trim());
        node.connectAsync(host, port);
    }

    private void connectToDiscovered() {
        if (node == null) {
            JOptionPane.showMessageDialog(this, "Inicie o nó antes de conectar.", "Atenção", JOptionPane.WARNING_MESSAGE);
            return;
        }
        String sel = discoveredList.getSelectedValue();
        if (sel == null) return;
        // formato "host:port (name)"
        try {
            int idxColon = sel.indexOf(':');
            int idxSpace = sel.indexOf(' ', idxColon + 1);
            String host = sel.substring(0, idxColon);
            int port = Integer.parseInt(sel.substring(idxColon + 1, idxSpace));
            node.connectAsync(host, port);
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, "Entrada inválida: " + sel, "Erro", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void sendChat() {
        if (node == null) {
            JOptionPane.showMessageDialog(this, "Inicie o nó para enviar mensagens.", "Atenção", JOptionPane.WARNING_MESSAGE);
            return;
        }
        String text = inputField.getText().trim();
        if (text.isEmpty()) return;
        node.broadcastChat(text);
        inputField.setText("");
    }

    private void performShutdown() {
        stopNode();
    }

    private void appendSystem(String msg) {
        SwingUtilities.invokeLater(() -> {
            String line = String.format("[%tT] [sistema] %s", new Date(), msg);
            chatArea.append(line + "\n");
            if (history != null) history.append(line);
            chatArea.setCaretPosition(chatArea.getDocument().getLength());
        });
    }

    private static String key(PeerInfo p) {
        return p.getHost() + ":" + p.getPort() + " (" + p.getName() + ")";
    }

    private static boolean containsValue(DefaultListModel<String> model, String val) {
        for (int i = 0; i < model.getSize(); i++) {
            if (Objects.equals(model.get(i), val)) return true;
        }
        return false;
    }
}