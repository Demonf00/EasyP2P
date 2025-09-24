# P2P Gomoku Hub — Cross-Network Ready (WebRTC)

**What's new**
- Signaling serves **WSS `/ws`** and **HTTP `/ice`** on the same port.
- Client auto-detects signaling URL:
  - Dev (Vite preview on 5173): `ws://<host>:8788/ws` & `http://<host>:8788/ice`
  - Prod (HTTPS): `wss://<host>/ws` & `https://<host>/ice`
- Optional TURN via env: `TURN_URLS`, `TURN_USER`, `TURN_PASS`.

## Dev
```powershell
# signaling
cd signaling-server
$env:BIND_HOST="0.0.0.0"
$env:PORT="8788"
# optional
# $env:TURN_URLS="turns:turn.your.com:443,turn:turn.your.com:3478"
# $env:TURN_USER="gameuser"
# $env:TURN_PASS="StrongPassword123"
npm i
npm start
```
```powershell
# frontend
cd ../app
npm i
npm run build
npm run preview -- --host 0.0.0.0 --port 5173
```
Open http://<your-ip>:5173

## Prod
Serve `app/dist` via HTTPS, and reverse-proxy `/ws` and `/ice` to the signaling
(see `deploy/nginx-signal.conf`). Add TURN (coturn), see `deploy/turnserver.conf.example`.
