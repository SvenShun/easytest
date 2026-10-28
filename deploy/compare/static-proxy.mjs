// Minimal static + API reverse proxy used only to run the upstream UIs
// next to their local Java services (no product code changes).
// usage: node static-proxy.mjs <port> <webRoot> <apiTarget> <apiPrefix|-> <mode:all|prefix|fallback>
import http from 'node:http';
import fs from 'node:fs';
import path from 'node:path';
import net from 'node:net';
import { fileURLToPath } from 'node:url';

const [portArg, rootArg, targetArg, prefixArg = '-', mode = 'fallback'] = process.argv.slice(2);
const port = Number(portArg || 5100);
const root = path.resolve(rootArg || '.');
const target = new URL(targetArg || 'http://127.0.0.1:8080');
const prefix = prefixArg === '-' ? '' : prefixArg;

const MIME = {
  '.html': 'text/html; charset=utf-8', '.js': 'application/javascript; charset=utf-8',
  '.css': 'text/css; charset=utf-8', '.json': 'application/json; charset=utf-8',
  '.svg': 'image/svg+xml', '.png': 'image/png', '.jpg': 'image/jpeg', '.jpeg': 'image/jpeg',
  '.gif': 'image/gif', '.ico': 'image/x-icon', '.woff': 'font/woff', '.woff2': 'font/woff2',
  '.ttf': 'font/ttf', '.map': 'application/json; charset=utf-8',
};

function staticFile(pathname) {
  const decoded = decodeURIComponent(pathname);
  const candidate = path.resolve(root, `.${decoded}`);
  if (!candidate.startsWith(root)) return null;
  try {
    if (fs.statSync(candidate).isFile()) return candidate;
  } catch { /* miss */ }
  return null;
}

function serveStatic(req, res, file) {
  const ext = path.extname(file).toLowerCase();
  res.writeHead(200, { 'Content-Type': MIME[ext] || 'application/octet-stream', 'Cache-Control': ext === '.html' ? 'no-store' : 'public, max-age=3600' });
  fs.createReadStream(file).pipe(res);
}

function proxyHttp(req, res) {
  const headers = { ...req.headers, host: target.host };
  const request = http.request({
    hostname: target.hostname, port: target.port || 80, method: req.method,
    path: req.url, headers,
  }, (upstream) => {
    res.writeHead(upstream.statusCode || 502, upstream.headers);
    upstream.pipe(res);
  });
  request.on('error', (err) => {
    res.writeHead(502, { 'Content-Type': 'text/plain; charset=utf-8' });
    res.end(`upstream unavailable: ${err.message}`);
  });
  req.pipe(request);
}

const server = http.createServer((req, res) => {
  const pathname = new URL(req.url, 'http://localhost').pathname;
  const wantsApi = mode === 'all'
    ? !pathname.startsWith('/assets/') && !pathname.startsWith('/svg/') && !pathname.startsWith('/images/')
      && !pathname.startsWith('/fonts/') && !pathname.startsWith('/templates/') && pathname !== '/'
    : prefix && pathname.startsWith(prefix);
  if (wantsApi && pathname !== '/' && pathname !== '/index.html') {
    proxyHttp(req, res);
    return;
  }
  const file = pathname === '/' ? path.join(root, 'index.html') : staticFile(pathname);
  if (file && fs.existsSync(file)) {
    serveStatic(req, res, file);
    return;
  }
  if (mode === 'fallback' || pathname === '/') {
    const index = path.join(root, 'index.html');
    if (fs.existsSync(index)) { serveStatic(req, res, index); return; }
  }
  res.writeHead(404, { 'Content-Type': 'text/plain; charset=utf-8' });
  res.end('not found');
});

server.on('upgrade', (req, socket, head) => {
  const upstream = net.connect(Number(target.port || 80), target.hostname, () => {
    const headers = Object.entries(req.headers).map(([k, v]) => `${k}: ${v}`).join('\r\n');
    upstream.write(`${req.method} ${req.url} HTTP/1.1\r\n${headers}\r\n\r\n`);
    if (head?.length) upstream.write(head);
    upstream.pipe(socket);
    socket.pipe(upstream);
  });
  upstream.on('error', () => socket.destroy());
});

server.listen(port, '0.0.0.0', () => {
  console.log(`[static-proxy] http://0.0.0.0:${port} root=${root} api=${target} prefix=${prefix || '(all)'} mode=${mode}`);
});
