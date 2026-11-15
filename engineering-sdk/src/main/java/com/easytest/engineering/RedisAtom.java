package com.easytest.engineering;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.Socket;
import java.nio.charset.StandardCharsets;

/** Minimal RESP atom for Redis cache/token assertions without a client dependency. */
public final class RedisAtom {

    private RedisAtom() {
    }

    public static String command(String host, int port, String... args) {
        try (Socket socket = new Socket(host, port)) {
            socket.setSoTimeout(5000);
            OutputStream out = socket.getOutputStream();
            StringBuilder request = new StringBuilder("*").append(args.length).append("\r\n");
            for (String arg : args) {
                byte[] bytes = arg.getBytes(StandardCharsets.UTF_8);
                request.append("$").append(bytes.length).append("\r\n").append(arg).append("\r\n");
            }
            out.write(request.toString().getBytes(StandardCharsets.UTF_8));
            out.flush();
            BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream(),
                    StandardCharsets.UTF_8));
            return readReply(reader);
        } catch (Exception e) {
            throw new IllegalStateException("Redis atom failed: " + e.getMessage(), e);
        }
    }

    private static String readReply(BufferedReader reader) throws Exception {
        String line = reader.readLine();
        if (line == null) return null;
        if (line.startsWith("$")) {
            int length = Integer.parseInt(line.substring(1));
            if (length < 0) return null;
            char[] buffer = new char[length + 2];
            int read = reader.read(buffer);
            return new String(buffer, 0, Math.min(length, Math.max(0, read))).replace("\r\n", "");
        }
        return line;
    }
}
