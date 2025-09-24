# P2P Gomoku Hub — WebRTC first, WS Relay fallback

- **WebRTC (STUN only)** path tries first.
- If it cannot connect within ~7s (e.g., symmetric NAT, no TURN), it **falls back to WS Relay**.
- The relay is a simple Java WebSocket server (module `java-relay`) you can run on any machine and expose via port forwarding.
- This mirrors your idea of using your existing Java NAT helper, but in a browser-compatible way (WebSocket).

## Dev quick start

### Signaling (Node, room code + /ice)
```powershell
cd signaling-server
$env:BIND_HOST="0.0.0.0"
$env:PORT="8788"
npm i
npm start
```

### Java WS Relay (no TURN needed)
```bash
cd java-relay
mvn -q -DskipTests package
java -jar target/ws-relay-0.1.0.jar -Dhost=0.0.0.0 -Dport=8989
# Forward 8989 from your router to this host for cross-network play
```

### Frontend
```powershell
cd app
npm i
npm run build
npm run preview -- --host 0.0.0.0 --port 5173
```
Open http://<your-ip>:5173 and play.  
(You can override endpoints with `.env.local`: `VITE_SIGNALING_URL`, `VITE_RELAY_URL`).

## Production
- Serve frontend over **HTTPS**.
- Reverse-proxy:
  - WSS signaling at `/ws` → Node server 8788
  - WSS relay at `/relay` → Java relay 8989
  - `/ice` → Node server 8788
- No TURN needed (relay will be used when WebRTC fails).

## Why this works
Browsers cannot use your raw TCP/UDP forwarder directly. WebRTC without TURN may fail across certain NATs.
This relay keeps your **“两端不同网段也能玩”**目标，同时完全兼容浏览器（WebSocket）。
