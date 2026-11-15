#!/usr/bin/env python3
from http.server import ThreadingHTTPServer, SimpleHTTPRequestHandler
import os
os.chdir(os.path.dirname(os.path.abspath(__file__)))
ThreadingHTTPServer(("127.0.0.1", 18090), SimpleHTTPRequestHandler).serve_forever()
