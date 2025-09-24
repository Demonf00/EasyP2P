import { WebSocketServer } from 'ws';
import { customAlphabet } from 'nanoid';

const alphabet = '0123456789ABCDEFGHJKLMNPQRSTVWXYZ';
const nanoid = customAlphabet(alphabet, 7);

function checksum(code) {
  const mod = 31; let a = 0;
  for (let i = 0; i < code.length; i++) a = (a * 33 + alphabet.indexOf(code[i])) % mod;
  return alphabet[a % alphabet.length];
}

const port = process.env.PORT ? Number(process.env.PORT) : 8788;
const host = process.env.BIND_HOST || '127.0.0.1';
const wss  = new WebSocketServer({ host, port });

/** code -> Set<ws> */
const rooms = new Map();

function broadcast(set, data, except) {
  for (const peer of set) if (peer !== except && peer.readyState === 1) peer.send(data);
}

wss.on('connection', (ws) => {
  ws.on('message', (buf) => {
    const raw = buf.toString();
    console.log('[signaling] message', raw);
    let msg; try { msg = JSON.parse(raw); } catch { return; }

    if (msg.type === 'create') {
      console.log('[signaling] create');
      let base; do { base = nanoid(); } while (rooms.has(base));
      const code = base + checksum(base);
      const set = new Set([ws]); rooms.set(code, set); ws.roomCode = code;
      ws.send(JSON.stringify({ type: 'created', code }));
      return;
    }

    if (msg.type === 'join') {
      console.log('[signaling] join', msg.code);
      const code = msg.code; const set = rooms.get(code);
      if (!set) { ws.send(JSON.stringify({ type: 'error', reason: 'NO_ROOM' })); return; }
      set.add(ws); ws.roomCode = code;
      broadcast(set, JSON.stringify({ type: 'peer-join' }), null);
      ws.send(JSON.stringify({ type: 'joined', code }));
      return;
    }

    if (msg.type === 'signal') {
      console.log('[signaling] signal');
      const payload = msg.payload;
      const set = rooms.get(ws.roomCode); if (!set) return;
      for (const peer of set) if (peer !== ws && peer.readyState === 1)
        peer.send(JSON.stringify({ type: 'signal', payload }));
      return;
    }

    if (msg.type === 'leave') {
      const set = rooms.get(ws.roomCode); if (!set) return;
      set.delete(ws); if (set.size === 0) rooms.delete(ws.roomCode);
      return;
    }
  });

  ws.on('close', () => {
    const set = rooms.get(ws.roomCode); if (!set) return;
    set.delete(ws); if (set.size === 0) rooms.delete(ws.roomCode);
    else broadcast(set, JSON.stringify({ type: 'peer-leave' }), null);
  });
});

wss.on('error', (err) => console.error('[signaling] server error:', err?.message));
console.log(`[signaling] listening on ${host}:${port}`);
