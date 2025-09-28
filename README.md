# easy-p2p

Port-forwarding P2P demo with **invite code**, **console log**, **sidebar game selection**, and **board canvas** (Gomoku-style). No STUN/TURN/ZeroTier required. Use manual port forwarding or IPv6.

## Build
```bash
mvn -v          # ensure Maven is installed
mvn clean package
```

This produces a fat JAR:
```
target/easy-p2p-1.0.0-shaded.jar
```

## Run
### Server (generate invite)
```
java -jar target/easy-p2p-1.0.0-shaded.jar
```
Click “我是服务器(生成邀请码)” → copy the invite code and send to your peer.
Make sure your router forwards the selected TCP port (default 2266) to your host.

### Client (paste invite)
```
java -jar target/easy-p2p-1.0.0-shaded.jar
```
Paste the invite code and click connect.

## Notes
- The app fetches your public IPv4 via `checkip.amazonaws.com` for invite code.
- Use manual port forwarding (IPv4) or allow inbound (IPv6) in your router/firewall.
- Invite is AES-256-CBC (+ Base64). You can rotate the key in `InviteCodec`.
