import React, { useMemo, useRef, useState } from 'react';
import { Room } from '../net/room';
import { games } from '../games';
import { Gomoku } from '../games/gomoku/rules';
import GomokuBoard from './GomokuBoard';

const STUNS: RTCIceServer[] = [
  { urls: [
      'stun:stun.l.google.com:19302',
      'stun:stun1.l.google.com:19302',
      'stun:stun2.l.google.com:19302',
      'stun:stun3.l.google.com:19302',
      'stun:stun4.l.google.com:19302',
    ] },
  // Add your TURN here for NAT fallback:
  // { urls: 'turn:your.turn.server:3478', username: 'u', credential: 'p' },
];

export default function App() {
  const [code, setCode] = useState('');
  const [connected, setConnected] = useState(false);
  const [mySide, setMySide] = useState<0|1>(0);
  const [gameId, setGameId] = useState<string>(Gomoku.id);
  const [state, setState] = useState(Gomoku.setup());

  const wsUrl = useMemo(() => {
    return (import.meta as any).env?.VITE_SIGNALING_URL || 'ws://127.0.0.1:8788';
  }, []);

  const room = useMemo(()=> new Room({ signalingUrl: wsUrl, iceServers: STUNS }), [wsUrl]);
  const roomRef = useRef(room);

  room.onPeerData = (data) => {
    try {
      const msg = JSON.parse(String(data));
      if (msg.t === 'hello') {
        setMySide(msg.side === 0 ? 1 : 0);
        const g = games[gameId]; roomRef.current.send({ t:'sync', state: g.serialize(state) });
      } else if (msg.t === 'move') {
        const g = games[gameId]; const next = g.apply(state, msg.move, (state.turn as 0|1));
        setState(next);
      } else if (msg.t === 'sync') {
        const g = games[gameId]; setState(g.deserialize(msg.state));
      }
    } catch {}
  };

  const host = () => {
    setMySide(0);
    room.connectAsHost();
    const id = setInterval(()=> {
      if (room.code) { setCode(room.code); clearInterval(id); }
    }, 100);
  };
  const join = () => { setMySide(1); room.connectAsGuest(code.trim().toUpperCase()); };

  const onPlace = (x:number, y:number) => {
    const g = games[gameId];
    if (!g.isLegal(state, {x,y}, mySide)) return;
    const next = g.apply(state, {x,y}, mySide);
    setState(next);
    room.send({ t:'move', move: {x,y} });
  };

  room.onOpen = () => { setConnected(true); room.send({ t: 'hello', game: gameId, side: mySide }); };
  room.onError = (reason) => {
    alert(reason === 'NO_ROOM' ? '房主还没创建房间，请稍后再点 Join。' : `信令错误：${reason}`);
  };

  return (
    <div style={{ padding: 24, maxWidth: 900, margin: '0 auto' }}>
      <h1 style={{ fontSize: 22, fontWeight: 700, marginBottom: 6 }}>P2P Game Hub</h1>
      <p style={{ fontSize: 13, opacity: 0.8, marginBottom: 12 }}>Select a game, host or join with a code. All gameplay goes over P2P WebRTC.</p>

      <div className="panel" style={{ display: 'grid', gap: 8, gridTemplateColumns: '1fr 1fr 2fr' }}>
        <select className="input" value={gameId} onChange={e=> setGameId(e.target.value)}>
          {Object.values(games).map(g => <option key={g.id} value={g.id}>{g.name}</option>)}
        </select>
        <button className="btn" onClick={host}>Host (get code)</button>
        <div style={{ display: 'flex', gap: 8 }}>
          <input className="input" placeholder="Enter Code" value={code} onChange={e=> setCode(e.target.value)} style={{ flex: 1, textTransform:'uppercase' }} />
          <button className="btn" onClick={join}>Join</button>
        </div>
      </div>

      {room.code && (
        <div style={{ marginTop: 8, fontSize: 13 }}>Share this code: <b>{room.code}</b></div>
      )}

      <div style={{ marginTop: 18 }}>
        <GomokuBoard s={state} onPlace={onPlace} mySide={mySide} />
      </div>

      <div style={{ marginTop: 10, fontSize: 13, color: connected ? 'green' : 'orange' }}>
        {connected ? 'Connected' : 'Not connected'}
      </div>
    </div>
  );
}
