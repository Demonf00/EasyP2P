import Peer from 'simple-peer';

export type PeerRole = 'host' | 'guest';

export interface RoomOptions {
  signalingUrl: string; // ws(s)://host[:port]/ws
  iceServers: RTCIceServer[];
}

export class Room {
  private ws!: WebSocket;
  private peers: Peer.Instance[] = [];
  private role: PeerRole = 'host';
  private options: RoomOptions;
  code = '';

  onOpen?: () => void;
  onClose?: () => void;
  onPeerData?: (data: Uint8Array | string) => void;
  onPeersChange?: (n: number) => void;
  onError?: (reason: string) => void;

  constructor(opts: RoomOptions) { this.options = opts; }

  connectAsHost() {
    this.role = 'host';
    this.ws = new WebSocket(this.options.signalingUrl);
    this.ws.onopen = () => { console.log('[ws] open'); this.ws.send(JSON.stringify({ type: 'create' })); };
    this.ws.onmessage = (ev) => this.handleSignal(JSON.parse(ev.data));
    this.ws.onerror = (e) => console.error('[ws] error', e);
    this.ws.onclose = () => { console.warn('[ws] closed'); this.onClose?.(); };
  }

  connectAsGuest(code: string) {
    this.role = 'guest'; this.code = code;
    this.ws = new WebSocket(this.options.signalingUrl);
    this.ws.onopen = () => { console.log('[ws] open'); this.ws.send(JSON.stringify({ type: 'join', code })); };
    this.ws.onmessage = (ev) => this.handleSignal(JSON.parse(ev.data));
    this.ws.onerror = (e) => console.error('[ws] error', e);
    this.ws.onclose = () => { console.warn('[ws] closed'); this.onClose?.(); };
  }

  private createPeer(initiator: boolean) {
    const peer = new Peer({
      initiator,
      trickle: true,
      config: { iceServers: this.options.iceServers },
      channelName: 'game',
    });
    peer.on('signal', (signal: any) => {
      if (this.ws && this.ws.readyState === WebSocket.OPEN) {
        this.ws.send(JSON.stringify({ type: 'signal', payload: signal }));
      }
    });
    peer.on('connect', () => this.onOpen?.());
    peer.on('data', (data: any) => this.onPeerData?.(data));
    peer.on('close', () => this.onPeersChange?.(this.peers.length));
    peer.on('error', (e) => console.error('[peer] error', e));
    this.peers.push(peer);
    this.onPeersChange?.(this.peers.length);
    return peer;
  }

  send(obj: any) {
    const payload = JSON.stringify(obj);
    for (const p of this.peers) {
      const ch: any = (p as any)._channel;
      if (ch && ch.readyState === 'open') p.send(payload);
    }
  }

  close() { for (const p of this.peers) p.destroy(); this.ws?.close(); }

  private handleSignal(msg: any) {
    try { console.log('[ws] recv', msg); } catch {}
    if (msg.type === 'error') {
      this.onError?.(msg.reason);
      if (msg.reason === 'NO_ROOM' && this.role === 'guest' && this.code) {
        setTimeout(() => {
          if (this.ws?.readyState === WebSocket.OPEN) {
            this.ws.send(JSON.stringify({ type: 'join', code: this.code }));
          }
        }, 1000);
      }
      return;
    }
    if (msg.type === 'created') { this.code = msg.code; }
    else if (msg.type === 'peer-join' && this.role === 'host') {
      this.createPeer(true);
    }
    else if (msg.type === 'signal') {
      if (this.peers.length === 0) this.createPeer(this.role === 'host'); // guest creates on first signal
      this.peers[0].signal(msg.payload);
    }
  }
}
