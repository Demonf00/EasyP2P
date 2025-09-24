# P2P Gomoku Game Hub (WebRTC)

- Signaling server: Node + ws (room code create/join & signal broadcast)
- Client: Vite + React + TypeScript + simple-peer (WebRTC DataChannel)
- Polyfills for browser: global/process/Buffer shims
- Google STUN pre-configured; add your TURN in `App.tsx` when needed
- Preview mode (no HMR) for stability

## Run

### 1) Signaling server
```powershell
cd signaling-server
$env:BIND_HOST="0.0.0.0"   # only local? use 127.0.0.1
$env:PORT="8788"
npm i
npm start
```

### 2) Frontend
```powershell
cd ../app
# uses .env.local (ws://127.0.0.1:8788) by default
npm i
npm run build
npm run preview -- --host 0.0.0.0 --port 5173
```
Open http://localhost:5173

### 3) Flow
A tab clicks **Host** (you must see `Share this code: XXXXXXXX`),  
B tab enters that code **Join** → status becomes **Connected**.

## Notes
- To expose on LAN: set `BIND_HOST=0.0.0.0` (server) and `VITE_SIGNALING_URL=ws://<your-LAN-IP>:8788` (client).
- Network → WS frames should show `created / peer-join / signal` if things are working.
