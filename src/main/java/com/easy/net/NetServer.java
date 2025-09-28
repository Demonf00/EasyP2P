package com.easy.net;

import com.easy.ui.ConsoleSink;
import com.easy.ui.MoveListener;
import com.easy.ui.NetEventListener;
import com.easy.ui.MoveSender;
import org.json.JSONObject;

import java.io.*;
import java.net.*;
import java.util.concurrent.atomic.AtomicBoolean;

public class NetServer extends Thread implements MoveSender {
    private final int port;
    private final ConsoleSink log;
    private final MoveListener listener;
    private final NetEventListener events;

    private ServerSocket ss;
    private Socket conn;
    private OutputStream out;
    private BufferedReader br;
    private final AtomicBoolean running = new AtomicBoolean(false);

    public NetServer(int port, ConsoleSink log, MoveListener listener){ this(port, log, listener, null); }

    public NetServer(int port, ConsoleSink log, MoveListener listener, NetEventListener events){
        this.port = port;
        this.log = log;
        this.listener = listener; this.events = events;
    }

    @Override
    public void run() {
        try {
            log.println("服务器监听端口 " + port + "（请手动配置路由器端口映射）");
            ss = new ServerSocket(port);
            running.set(true);
            log.println("等待客户端连接...");
            conn = ss.accept();
            log.println("客户端已连接: " + conn.getRemoteSocketAddress());
            out = conn.getOutputStream();
            br = new BufferedReader(new InputStreamReader(conn.getInputStream(), "UTF-8"));
            Proto.sendJSON(out, Proto.hello("server"));

            JSONObject jo;
            while ((jo = Proto.readJSON(br)) != null) {
                String type = jo.optString("type", "");
                if ("MOVE".equals(type)) {
                    int x = jo.getInt("x"), y = jo.getInt("y");
                    log.println("收到对方落子 x=" + x + " y=" + y);
                    if (listener != null) listener.onOpponentMove(x, y);
                    Proto.sendJSON(out, Proto.ack());
                } else if ("GAME".equals(type)) {
                    String cmd = jo.optString("cmd", "");
                    String g = jo.optString("game", "GOMOKU");
                    String starter = jo.optString("starter", "host");
                    if (events != null) {
                        if ("select".equals(cmd)) events.onGameSelected(com.easy.game.GameType.from(g), starter);
                        else if ("suggest".equals(cmd)) events.onGameSuggested(com.easy.game.GameType.from(g));
                    }
                    log.println("收到游戏事件: " + jo.toString());
                } else {
                    log.println("收到: " + jo.toString());
                }
            }
            log.println("连接结束");
        } catch (Exception e) {
            log.println("Server异常: " + e.getMessage());
        } finally {
            close();
        }
    }

    public void close() {
        running.set(false);
        try { if (conn != null) conn.close(); } catch (Exception ignore) {}
        try { if (ss != null) ss.close(); } catch (Exception ignore) {}
    }

    
    public void sendJson(org.json.JSONObject jo) throws IOException {
        if (out == null) throw new IOException("尚未连接");
        com.easy.net.Proto.sendJSON(out, jo);
    }

    @Override
    public void sendMove(int x, int y, int turn, String hash) throws IOException {
        if (out == null) throw new IOException("尚未有客户端连接");
        Proto.sendJSON(out, Proto.move(x, y, turn, hash));
    }
}
