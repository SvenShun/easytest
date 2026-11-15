#!/usr/bin/env python3
"""Minimal MQTT mock: accepts CONNECT, replies CONNACK, logs QoS0 PUBLISH."""
import socket
import sys
import threading

PORT = int(sys.argv[1]) if len(sys.argv) > 1 else 1883


def handle(conn, addr):
    try:
        data = conn.recv(4096)
        if not data:
            return
        print("[mqtt] CONNECT", addr, data.hex(), flush=True)
        conn.sendall(bytes([0x20, 0x02, 0x00, 0x00]))
        conn.settimeout(10)
        while True:
            chunk = conn.recv(4096)
            if not chunk:
                break
            print("[mqtt] PACKET", addr, chunk.hex(), flush=True)
    except Exception as exc:
        print("[mqtt] error", addr, exc, flush=True)
    finally:
        conn.close()


def main():
    server = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
    server.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
    server.bind(("127.0.0.1", PORT))
    server.listen(20)
    print(f"[mqtt] listening on :{PORT}", flush=True)
    while True:
        conn, addr = server.accept()
        threading.Thread(target=handle, args=(conn, addr), daemon=True).start()


if __name__ == "__main__":
    main()
