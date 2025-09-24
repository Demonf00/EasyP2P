# WS Relay (Java)

A minimal WebSocket relay so browsers can connect across different networks **without TURN**.
Both peers connect to this server at `/relay`, send `{type:"join", code:"ABCDEFG"}`,
and any subsequent messages are forwarded between them.

## Build & Run
```bash
mvn -q -e -DskipTests package
java -jar target/ws-relay-0.1.0.jar -Dhost=0.0.0.0 -Dport=8989
# expose 8989 on your router / firewall
```
In production, put it behind Nginx with TLS (WSS).
