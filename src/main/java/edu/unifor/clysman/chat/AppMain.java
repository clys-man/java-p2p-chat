package edu.unifor.clysman.chat;

import edu.unifor.clysman.chat.gui.ChatWindow;

import javax.swing.*;

public class AppMain {
    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            try {
                UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
            } catch (Exception ignored) {}
            new ChatWindow().setVisible(true);
        });
    }
}