package edu.unifor.clysman.chat.service;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;

public class MessageHistory implements Closeable {

    private BufferedWriter out;
    private final SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd_HH-mm-ss");

    public void start(String userName, int port) throws IOException {
        String ts = sdf.format(new Date());
        File dir = new File("history");
        if (!dir.exists()) dir.mkdirs();
        File f = new File(dir, "chat_" + userName + "_" + port + "_" + ts + ".log");
        out = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(f, true), StandardCharsets.UTF_8));
        append("== Nova sessão iniciada em " + new Date() + " ==");
    }

    public synchronized void append(String line) {
        try {
            if (out != null) {
                out.write(line);
                out.write("\n");
                out.flush();
            }
        } catch (IOException ignored) {}
    }

    @Override
    public void close() {
        try {
            if (out != null) {
                append("== Sessão encerrada em " + new Date() + " ==");
                out.close();
            }
        } catch (IOException ignored) {}
    }
}