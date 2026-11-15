package com.easytest.engineering;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

/**
 * Minimal MQTT 3.1.1 QoS0 publisher used by test engineering projects.
 * For TLS/HA setups replace with the enterprise MQTT client; the atom API stays stable.
 */
public final class MqttAtom {

    private MqttAtom() {
    }

    public static void publish(String host, int port, String clientId, String topic, String payload,
                               String username, String password, int keepAliveSeconds) {
        String client = clientId == null || clientId.isBlank()
                ? "easytest-" + UUID.randomUUID().toString().substring(0, 8) : clientId;
        try (Socket socket = new Socket(host, port)) {
            socket.setSoTimeout(5000);
            OutputStream out = socket.getOutputStream();
            InputStream in = socket.getInputStream();
            byte[] connect = connectPacket(client, username, password, keepAliveSeconds);
            out.write(connect);
            out.flush();
            int first = in.read();
            int second = in.read();
            if (first != 0x20 || second < 2) throw new IllegalStateException("MQTT CONNACK failed");
            out.write(publishPacket(topic, payload));
            out.write(new byte[]{(byte) 0xE0, 0x00});
            out.flush();
        } catch (Exception e) {
            throw new IllegalStateException("MQTT publish failed: " + e.getMessage(), e);
        }
    }

    private static byte[] connectPacket(String clientId, String username, String password, int keepAlive) {
        ByteArrayOutputStream body = new ByteArrayOutputStream();
        writeString(body, "MQTT");
        body.write(4);
        int flags = 0x02;
        if (username != null) flags |= 0x80;
        if (password != null) flags |= 0x40;
        body.write(flags);
        body.write((keepAlive >> 8) & 0xFF);
        body.write(keepAlive & 0xFF);
        writeString(body, clientId);
        if (username != null) writeString(body, username);
        if (password != null) writeString(body, password);
        return packet(0x10, body.toByteArray());
    }

    private static byte[] publishPacket(String topic, String payload) {
        ByteArrayOutputStream body = new ByteArrayOutputStream();
        writeString(body, topic);
        byte[] bytes = payload == null ? new byte[0] : payload.getBytes(StandardCharsets.UTF_8);
        body.write(bytes, 0, bytes.length);
        return packet(0x30, body.toByteArray());
    }

    private static void writeString(ByteArrayOutputStream out, String value) {
        byte[] bytes = value == null ? new byte[0] : value.getBytes(StandardCharsets.UTF_8);
        out.write((bytes.length >> 8) & 0xFF);
        out.write(bytes.length & 0xFF);
        out.write(bytes, 0, bytes.length);
    }

    private static byte[] packet(int header, byte[] body) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write(header);
        int length = body.length;
        do {
            int digit = length % 128;
            length /= 128;
            if (length > 0) digit |= 0x80;
            out.write(digit);
        } while (length > 0);
        out.write(body, 0, body.length);
        return out.toByteArray();
    }
}
