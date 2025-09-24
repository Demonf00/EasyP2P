import React, { useEffect, useMemo, useRef, useState } from 'react';
import { Room } from '../net/room';
import { games } from '../games';
import { Gomoku } from '../games/gomoku/rules';
import GomokuBoard from './GomokuBoard';

const STUNS_FALLBACK: RTCIceServer[] = [
  { urls: [
      'stun:stun.l.google.com:19302',
      'stun:stun1.l.google.com:19302',
      'stun:stun2.l.google.com:19302',
      'stun:stun3.l.google.com:19302',
      'stun:stun4.l.google.com:19302'
    ] },
];

export default function App() {
  const [code, setCode] = useState('');
  const [connected, setConnected] = useState(false);
  const [mySide, setMySide] = useState<0|1>(0);
  const [gameId, setGameId] = useState<string>(Gomoku.id);
  const [state, setState] = useState(Gomoku.setup());
  const [ice, setIce] = useState<RTCIceServer[]>([]);
  const [transport, setTransport] = useState<'webrtc' | 'relay'>('webrtc');

  const wsUrl = useMemo(() => `${location.protocol === 'https:' ? 'wss' : 'ws'}://${location.host}/ws`, []);
  const iceUrl = useMemo(() => `${location.protocol}//${location.host}/ice`, []);
  const relayUrl = useMemo(() => `${location.protocol === 'https:' ? 'wss' : 'ws'}://${location.host}/relay`, []);

  useEffect(() => {
    fetch(iceUrl).then(r => r.json())
      .then(({ iceServers }) => setIce(iceServers))
      .catch(() => setIce(STUNS_FALLBACK));
  }, [iceUrl]);

  const room = useMemo(()=> new Room({ signalingUrl: wsUrl, iceServers: ice.length ? ice : STUNS_FALLBACK, relayUrl }), [wsUrl, ice, relayUrl]);
  const roomRef = useRef(room);

  room.onPeerData = (data) => {
    try {
      const msg = JSON.parse(String(data));
      if (msg.t === 'hello') {
        setMySide(msg.side === 0 ? 1 : 0);
        roomRef.current.send({ t:'sync', state: JSON.stringify(state) });
      } else if (msg.t === 'move') {
        const next = { ...state, board: state.board.map(r => r.slice()) } as any;
        next.board[msg.move.y][msg.move.x] = state.turn as 0|1;
        next.turn = (state.turn ^ 1) as 0|1;
        setState(next);
      } else if (msg.t === 'sync') {
        setState(JSON.parse(msg.state));
      }
    } catch {}
  };
  room.onTransportChange = (t) => setTransport(t);
  room.onOpen = () => { setConnected(true); room.send({ t:'hello', game: gameId, side: mySide }); };

  const host = () => {
    setMySide(0);
    room.connectAsHost();
    const id = setInterval(()=> {
      if (room.code) { setCode(room.code); clearInterval(id); }
    }, 100);
  };
  const join = () => { setMySide(1); room.connectAsGuest(code.trim().toUpperCase()); };

  const onPlace = (x:number, y:number) => {
    const next = { ...state, board: state.board.map(r=> r.slice()) } as any;
    if (next.board[y][x] !== -1) return;
    next.board[y][x] = mySide;
    next.turn = (state.turn ^ 1) as 0|1;
    setState(next);
    room.send({ t:'move', move: {x,y} });
  };

  return (
    <div style={{ padding: 24, maxWidth: 900, margin: '0 auto' }}>
      <h1 style={{ fontSize: 22, fontWeight: 700, marginBottom: 6 }}>P2P Game Hub (One-Box)</h1>
      <p style={{ fontSize: 13, opacity: 0.8, marginBottom: 12 }}>
        Single command: static + signaling + <b>WS relay</b>. Transport auto: WebRTC → Relay.
        <span style={{ marginLeft: 8 }} className="tag">transport: {transport}</span>
      </p>

      <div className="panel" style={{ display: 'grid', gap: 8, gridTemplateColumns: '1fr 1fr 2fr' }}>
        <select className="input" value={gameId} onChange={e=> setGameId(e.target.value)}>
          <option value={Gomoku.id}>{Gomoku.name}</option>
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
        {connected ? 'Connected' : 'Not connected'} (Host first, then Join with the same code)
      </div>
    </div>
  );
}
