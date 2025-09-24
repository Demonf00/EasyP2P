import Peer from 'simple-peer';
export type PeerRole='host'|'guest';
export interface RoomOptions{ signalingUrl:string; iceServers:RTCIceServer[]; relayUrl:string; }
type Transport='webrtc'|'relay';
export class Room{
  private ws!:WebSocket; private relay?:WebSocket; private peers:Peer.Instance[]=[]; private role:PeerRole='host';
  private options:RoomOptions; private transport:Transport='webrtc'; code='';
  onOpen?:()=>void; onClose?:()=>void; onPeerData?:(data:Uint8Array|string)=>void; onPeersChange?:(n:number)=>void; onError?:(r:string)=>void; onTransportChange?:(t:Transport)=>void;
  constructor(opts:RoomOptions){ this.options=opts; }
  connectAsHost(){ this.role='host'; this.ws=new WebSocket(this.options.signalingUrl); this.ws.onopen=()=>{ try{ this.ws.send(JSON.stringify({type:'create'})); }catch{} }; this.ws.onmessage=(ev)=>this.handleSignal(JSON.parse(ev.data)); this.ws.onclose=()=>this.onClose?.(); this.ws.onerror=()=>{}; }
  connectAsGuest(code:string){ this.role='guest'; this.code=code; this.ws=new WebSocket(this.options.signalingUrl); this.ws.onopen=()=>{ try{ this.ws.send(JSON.stringify({type:'join',code})); }catch{} }; this.ws.onmessage=(ev)=>this.handleSignal(JSON.parse(ev.data)); this.ws.onclose=()=>this.onClose?.(); this.ws.onerror=()=>{}; }
  private createPeer(initiator:boolean){ const peer=new Peer({initiator,trickle:true,config:{iceServers:this.options.iceServers},channelName:'game'}); let connected=false;
    const fallback=setTimeout(()=>{ if(!connected){ this.useRelay(); } },3000); // faster fallback
    peer.on('signal',(sig:any)=>{ try{ if(this.ws&&this.ws.readyState===WebSocket.OPEN){ this.ws.send(JSON.stringify({type:'signal',payload:sig})); } }catch{} });
    peer.on('connect',()=>{ connected=true; clearTimeout(fallback); this.transport='webrtc'; this.onTransportChange?.(this.transport); this.onOpen?.(); });
    peer.on('data',(d:any)=>this.onPeerData?.(d)); peer.on('close',()=>this.onPeersChange?.(this.peers.length)); peer.on('error',()=>{});
    this.peers.push(peer); this.onPeersChange?.(this.peers.length); return peer; }
  forceRelay(){ this.useRelay(); }
  private useRelay(){ if(this.transport==='relay') return; this.transport='relay'; this.onTransportChange?.(this.transport);
    this.relay=new WebSocket(this.options.relayUrl);
    this.relay.onopen=()=>{ try{ this.relay!.send(JSON.stringify({type:'join',code:this.code})); }catch{} this.onOpen?.(); };
    this.relay.onmessage=(ev)=>{ if(typeof ev.data==='string') this.onPeerData?.(ev.data); else this.onPeerData?.(ev.data as any); };
  }
  send(obj:any){ const payload=JSON.stringify(obj);
    if(this.transport==='relay'&&this.relay&&this.relay.readyState===WebSocket.OPEN){ this.relay.send(payload); return; }
    for(const p of this.peers){ const ch:any=(p as any)._channel; if(ch&&ch.readyState==='open') p.send(payload); }
  }
  private handleSignal(msg:any){ if(msg.type==='error'){ this.onError?.(msg.reason); return; } if(msg.type==='created'){ this.code=msg.code; } else if(msg.type==='peer-join'&&this.role==='host'){ this.createPeer(true); } else if(msg.type==='signal'){ if(this.peers.length===0) this.createPeer(this.role==='host'); this.peers[0].signal(msg.payload); } }
}
