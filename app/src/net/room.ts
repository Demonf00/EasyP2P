import Peer from 'simple-peer';
export type PeerRole = 'host' | 'guest';
export interface RoomOptions { signalingUrl: string; iceServers: RTCIceServer[]; relayUrl: string; }
type Transport = 'webrtc' | 'relay';
export class Room {
  private ws!: WebSocket; private relay?: WebSocket; private peers: Peer.Instance[] = []; private role: PeerRole = 'host';
  private options: RoomOptions; private transport: Transport = 'webrtc'; code = '';
  onOpen?: () => void; onClose?: () => void; onPeerData?: (data: Uint8Array | string) => void; onPeersChange?: (n: number) => void;
  onError?: (reason: string) => void; onTransportChange?: (t: Transport) => void;
  constructor(opts: RoomOptions) { this.options = opts; }
  connectAsHost() { this.role = 'host'; this.ws = new WebSocket(this.options.signalingUrl); this.ws.onopen = () => { this.ws.send(JSON.stringify({ type: 'create' })); }; this.ws.onmessage = (ev) => this.handleSignal(JSON.parse(ev.data)); this.ws.onclose = () => { this.onClose?.(); }; }
  connectAsGuest(code: string) { this.role = 'guest'; this.code = code; this.ws = new WebSocket(this.options.signalingUrl); this.ws.onopen = () => { this.ws.send(JSON.stringify({ type: 'join', code })); }; this.ws.onmessage = (ev) => this.handleSignal(JSON.parse(ev.data)); this.ws.onclose = () => { this.onClose?.(); }; }
  private createPeer(initiator: boolean) {
    const peer = new Peer({ initiator, trickle: true, config: { iceServers: this.options.iceServers }, channelName: 'game' });
    let connected = false; const fallbackTimer = setTimeout(() => { if (!connected) { this.useRelay(); } }, 7000);
    peer.on('signal', (signal: any) => { if (this.ws && this.ws.readyState === WebSocket.OPEN) { this.ws.send(JSON.stringify({ type: 'signal', payload: signal })); } });
    peer.on('connect', () => { connected = true; clearTimeout(fallbackTimer); this.transport = 'webrtc'; this.onTransportChange?.(this.transport); this.onOpen?.(); });
    peer.on('data', (data: any) => this.onPeerData?.(data)); peer.on('close', () => this.onPeersChange?.(this.peers.length)); peer.on('error', (e) => console.error('[peer] error', e));
    this.peers.push(peer); this.onPeersChange?.(this.peers.length); return peer;
  }
  private useRelay() { this.transport = 'relay'; this.onTransportChange?.(this.transport); this.relay = new WebSocket(this.options.relayUrl); this.relay.onopen = () => { this.relay!.send(JSON.stringify({ type: 'join', code: this.code })); this.onOpen?.(); }; this.relay.onmessage = (ev) => { if (typeof ev.data === 'string') this.onPeerData?.(ev.data); else this.onPeerData?.(ev.data as any); }; }
  send(obj: any) { const payload = JSON.stringify(obj); if (this.transport === 'relay' && this.relay && this.relay.readyState === WebSocket.OPEN) { this.relay.send(payload); return; } for (const p of this.peers) { const ch: any = (p as any)._channel; if (ch && ch.readyState === 'open') p.send(payload); } }
  private handleSignal(msg: any) { if (msg.type === 'error') { this.onError?.(msg.reason); return; } if (msg.type === 'created') { this.code = msg.code; } else if (msg.type === 'peer-join' && this.role === 'host') { this.createPeer(true); } else if (msg.type === 'signal') { if (this.peers.length === 0) this.createPeer(this.role === 'host'); this.peers[0].signal(msg.payload); } }
}
