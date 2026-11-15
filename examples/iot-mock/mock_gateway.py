#!/usr/bin/env python3
"""Local IoT gateway mock for EasyTest demo flow."""
import json
import sys
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from urllib.parse import urlparse

PORT = int(sys.argv[1]) if len(sys.argv) > 1 else 18080
DEVICES = {}


class Handler(BaseHTTPRequestHandler):
    def log_message(self, fmt, *args):
        print("[gateway]", self.address_string(), fmt % args, flush=True)

    def _json(self, status, payload):
        body = json.dumps(payload, ensure_ascii=False).encode()
        self.send_response(status)
        self.send_header("Content-Type", "application/json")
        self.send_header("Content-Length", str(len(body)))
        self.end_headers()
        self.wfile.write(body)

    def do_GET(self):
        if urlparse(self.path).path == "/health":
            self._json(200, {"status": "UP"})
        else:
            self._json(404, {"error": "not found"})

    def do_POST(self):
        length = int(self.headers.get("Content-Length", "0"))
        raw = self.rfile.read(length) if length else b"{}"
        try:
            body = json.loads(raw or b"{}")
        except json.JSONDecodeError:
            self._json(400, {"error": "invalid json"})
            return
        path = urlparse(self.path).path
        if path == "/api/auth/login":
            username = body.get("username")
            if not username or not body.get("password"):
                self._json(401, {"error": "invalid credentials"})
                return
            self._json(200, {"token": "local-token-" + username})
        elif path == "/api/device/bind":
            home = body.get("homeId")
            device = body.get("deviceId")
            if not home or not device:
                self._json(400, {"error": "homeId/deviceId required"})
                return
            DEVICES[device] = home
            self._json(200, {"ok": True, "homeId": home, "deviceId": device})
        else:
            self._json(404, {"error": "not found"})


if __name__ == "__main__":
    print(f"[gateway] listening on :{PORT}", flush=True)
    ThreadingHTTPServer(("127.0.0.1", PORT), Handler).serve_forever()
