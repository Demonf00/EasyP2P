import express from 'express';
import compression from 'compression';
import cors from 'cors';
import { createServer } from 'http';
import { WebSocketServer } from 'ws';
import { customAlphabet } from 'nanoid';
import path from 'path';
import { fileURLToPath } from 'url';

const __filename = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filename);

// HTTP for static + /ice
const PORT = Number(process.env.HTTP_PORT || 9000);
const HOST = process.env.HOST || '0.0.0.0';
const WS_SIGNAL_PORT = Number(process.env.WS_SIGNAL_PORT || 9001);
const WS_RELAY_PORT  = Number(process.env.WS_RELAY_PORT  || 9002);
const ALLOW_ORIGIN = process.env.CORS_ORIGIN || '*';

// ICE
const STUN = { urls: [
  'stun:stun.l.google.com:19302',
  'stun:stun1.l.google.com:19302',
  'stun:stun2.l.google.com:19302',
  'stun:stun3.l.google.com:19302',
  'stun:stun4.l.google.com:19302'
]};
const ICE_SERVERS = [STUN];

// Express
const app = express();
app.disable('x-powered-by');
app.use(compression());
app.use(cors({ origin: ALLOW_ORIGIN }));
app.get('/ice', (req, res) => res.json({ iceServers: ICE_SERVERS }));

const distDir = path.resolve(__dirname, '../app/dist');
app.use(express.static(distDir));
app.get('*', (_, res) => res.sendFile(path.join(distDir, 'index.html')));

const httpServer = createServer(app);
httpServer.listen(PORT, HOST, () => {
  console.log(`[hub] http://${HOST}:${PORT}  (static + /ice)`);
  console.log(`[hub] ws signaling: ws://${HOST}:${WS_SIGNAL_PORT}`);
  console.log(`[hub] ws relay:     ws://${HOST}:${WS_RELAY_PORT}`);
});

// ---- WS: signaling on dedicated port ----
const wssSignal = new WebSocketServer({ host: HOST, port: WS_SIGNAL_PORT });
const wssRelay  = new WebSocketServer({ host: HOST, port: WS_RELAY_PORT });

const alphabet = '0123456789ABCDEFGHJKLMNPQRSTVWXYZ';
const nanoid = customAlphabet(alphabet, 7);
function checksum(code) {
  const mod = 31; let a = 0;
  for (let i = 0; i < code.length; i++) a = (a * 33 + alphabet.indexOf(code[i])) % mod;
  return alphabet[a % alphabet.length];
}

// signaling rooms
const rooms = new Map();
wssSignal.on('connection', (ws, req) => {
  console.log('[ws] signaling connected from', req.socket.remoteAddress);
  ws.on('message', (buf) => {
    const raw = buf.toString();
    let msg; try { msg = JSON.parse(raw); } catch { return; }
    if (msg.type === 'create') {
      let base; do { base = nanoid(); } while (rooms.has(base));
      const code = base + checksum(base);
      const set = new Set([ws]); rooms.set(code, set); ws.roomCode = code;
      ws.send(JSON.stringify({ type: 'created', code }));
      return;
    }
    if (msg.type === 'join') {
      const set = rooms.get(msg.code);
      if (!set) { ws.send(JSON.stringify({ type: 'error', reason: 'NO_ROOM' })); return; }
      set.add(ws); ws.roomCode = msg.code;
      for (const peer of set) if (peer !== ws && peer.readyState === 1)
        peer.send(JSON.stringify({ type: 'peer-join' }));
      ws.send(JSON.stringify({ type: 'joined', code: msg.code }));
      return;
    }
    if (msg.type === 'signal') {
      const set = rooms.get(ws.roomCode); if (!set) return;
      for (const peer of set) if (peer !== ws && peer.readyState === 1)
        peer.send(JSON.stringify({ type: 'signal', payload: msg.payload }));
      return;
    }
  });
  ws.on('close', () => {
    const set = rooms.get(ws.roomCode); if (!set) return;
    set.delete(ws); if (set.size === 0) rooms.delete(ws.roomCode);
    else for (const peer of set) if (peer.readyState === 1)
      peer.send(JSON.stringify({ type: 'peer-leave' }));
  });
});

// relay rooms
const rrooms = new Map();
wssRelay.on('connection', (ws, req) => {
  console.log('[ws] relay connected from', req.socket.remoteAddress);
  ws.on('message', (buf) => {
    try {
      const s = buf.toString();
      const msg = JSON.parse(s);
      if (msg.type === 'join') {
        const code = String(msg.code || '').toUpperCase();
        const set = rrooms.get(code) || new Set();
        set.add(ws); rrooms.set(code, set); ws.rCode = code;
        for (const peer of set) if (peer !== ws && peer.readyState === 1)
          peer.send(JSON.stringify({ type: 'peer-join' }));
        return;
      }
    } catch {}
    const set = rrooms.get(ws.rCode);
    if (!set) return;
    for (const peer of set) if (peer !== ws && peer.readyState === 1)
      peer.send(buf);
  });
  ws.on('close', () => {
    const set = rrooms.get(ws.rCode); if (!set) return;
    set.delete(ws); if (set.size === 0) rrooms.delete(ws.rCode);
    else for (const peer of set) if (peer.readyState === 1)
      peer.send(JSON.stringify({ type: 'peer-leave' }));
  });
});
