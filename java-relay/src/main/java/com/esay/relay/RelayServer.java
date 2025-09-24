package com.esay.relay;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import org.java_websocket.WebSocket;
import org.java_websocket.handshake.ClientHandshake;
import org.java_websocket.server.WebSocketServer;

import java.net.InetSocketAddress;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class RelayServer extends WebSocketServer {
  private static final Gson GSON = new Gson();
  private final String path;
  private final Map<String, Set<WebSocket>> rooms = new ConcurrentHashMap<>();

  public RelayServer(InetSocketAddress addr, String path) {
    super(addr);
    this.path = path;
  }

  @Override public void onOpen(WebSocket conn, ClientHandshake handshake) {
    String reqPath = handshake.getResourceDescriptor();
    if (!reqPath.startsWith(path)) {
      conn.close(1008, "bad-path"); // policy violation
      return;
    }
    conn.setAttachment(new ClientState());
  }

  @Override public void onClose(WebSocket conn, int code, String reason, boolean remote) {
    ClientState st = (ClientState) conn.getAttachment();
    if (st != null && st.code != null) {
      Set<WebSocket> set = rooms.get(st.code);
      if (set != null) {
        set.remove(conn);
        if (set.isEmpty()) rooms.remove(st.code);
        // notify the remaining peer
        for (WebSocket peer : set) {
          JsonObject evt = new JsonObject();
          evt.addProperty("type", "peer-leave");
          peer.send(evt.toString());
        }
      }
    }
  }

  @Override public void onMessage(WebSocket conn, String message) {
    ClientState st = (ClientState) conn.getAttachment();
    try {
      JsonObject obj = GSON.fromJson(message, JsonObject.class);
      String type = obj.get("type").getAsString();
      if ("join".equals(type)) {
        String code = obj.get("code").getAsString();
        st.code = code;
        rooms.computeIfAbsent(code, k -> Collections.synchronizedSet(new HashSet<>())).add(conn);
        // notify peers
        for (WebSocket peer : rooms.get(code)) {
          if (peer != conn) {
            JsonObject evt = new JsonObject();
            evt.addProperty("type", "peer-join");
            peer.send(evt.toString());
          }
        }
        return;
      }
      // forward any other text payload to the other peer(s) in the room
      if (st.code != null) {
        Set<WebSocket> set = rooms.get(st.code);
        if (set != null) {
          for (WebSocket peer : set) if (peer != conn) peer.send(message);
        }
      }
    } catch (Exception e) {
      // ignore bad json
    }
  }

  @Override public void onError(WebSocket conn, Exception ex) {
    System.err.println("[relay] error: " + ex.getMessage());
  }

  @Override public void onStart() {
    setConnectionLostTimeout(30);
  }

  private static class ClientState {
    String code;
  }
}
