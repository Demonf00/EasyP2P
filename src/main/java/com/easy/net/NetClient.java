package com.easy.net;

import com.easy.ui.ConsoleSink;
import com.easy.ui.MoveListener;
import com.easy.ui.NetEventListener;
import com.easy.ui.MoveSender;
import org.json.JSONObject;

import java.io.*;
import java.net.*;

public class NetClient implements MoveSender {
    private final ConsoleSink log;
    private final MoveListener listener;
    private final NetEventListener events;

    private Socket socket;
    private BufferedReader br;
    private OutputStream out;

    private volatile boolean running = false;
    private Thread readThread;

    public NetClient(ConsoleSink log, MoveListener listener) { this(log, listener, null); }
    public NetClient(ConsoleSink log, MoveListener listener, NetEventListener events) {
        this.log = log;
        this.listener = listener;
        this.events = events;
    }

    /** 连接并启动读循环 */
    public void connect(String inviteCode) throws Exception {
        // 1) 解析邀请码
        InviteCodec.Endpoint ep = InviteCodec.parse(inviteCode == null ? null : inviteCode.replaceAll("\\s+",""));
        if (ep == null || ep.ip == null) throw new IllegalArgumentException("邀请码无效");
        log.println("[CLIENT] 解析邀请码 -> " + ep.ip + ":" + ep.port);

        // 2) 建立 TCP 连接（10s 超时）
        socket = new Socket();
        socket.setTcpNoDelay(true);
        socket.setKeepAlive(true);
        log.println("[CLIENT] connecting to " + ep.ip + ":" + ep.port + " ...");
        socket.connect(new InetSocketAddress(ep.ip, ep.port), 10_000);
        socket.setSoTimeout(15_000); // 读超时
        log.println("[CLIENT] connected. local=" + socket.getLocalSocketAddress());

        // 3) 构造 IO
        br  = new BufferedReader(new InputStreamReader(socket.getInputStream(), "UTF-8"));
        out = socket.getOutputStream();

        // 4) 主动发 HELLO（避免双方都在等）
        try {
            JSONObject helloMe = Proto.hello("client");
            log.println("[CLIENT] sending HELLO: " + helloMe);
            Proto.sendJSON(out, helloMe);
        } catch (Exception e) {
            log.println("[CLIENT] send HELLO failed: " + e.getClass().getName() + ": " + String.valueOf(e.getMessage()));
        }

        // 5) 期待服务端的 HELLO（可选）
        try {
            JSONObject helloSrv = Proto.readJSON(br);
            log.println("[CLIENT] server HELLO: " + helloSrv);
        } catch (SocketTimeoutException te) {
            log.println("[CLIENT] wait server HELLO timeout: " + te.getMessage());
        } catch (EOFException eof) {
            log.println("[CLIENT] server closed during HELLO: " + eof.getMessage());
            close();
            throw eof;
        } catch (Exception e) {
            log.println("[CLIENT] HELLO read error: " + e.getClass().getName() + ": " + String.valueOf(e.getMessage()));
        }

        // 6) 启动读循环
        running = true;
        loopReadAsync();
    }

    /** 后台读循环，带详细日志与超时处理 */
    private void loopReadAsync() {
        readThread = new Thread(() -> {
            log.println("[CLIENT] entering read loop...");
            try {
                while (running) {
                    JSONObject jo = Proto.readJSON(br); // 受 socket SoTimeout 保护
                    if (jo == null) {
                        log.println("[CLIENT] read JSON -> null，对端关闭？");
                        break;
                    }
                    String type = jo.optString("type", "");
                    if ("ACK".equals(type)) {
                        log.println("[CLIENT] 收到 ACK");
                    } else if ("MOVE".equals(type)) {
                        int x = jo.getInt("x"), y = jo.getInt("y");
                        log.println("[CLIENT] 收到对方落子 x=" + x + " y=" + y);
                        if (listener != null) listener.onOpponentMove(x, y);
                    } else if ("GAME".equals(type)) {
                        String cmd = jo.optString("cmd", "");
                        String g = jo.optString("game", "GOMOKU");
                        String starter = jo.optString("starter", "host");
                        if (events != null) {
                            if ("select".equals(cmd)) events.onGameSelected(com.easy.game.GameType.from(g), starter);
                            else if ("suggest".equals(cmd)) events.onGameSuggested(com.easy.game.GameType.from(g));
                            else if ("reset".equals(cmd)) events.onGameSelected(com.easy.game.GameType.from(g), "host");
                        }
                        log.println("[CLIENT] 收到游戏事件: " + jo);
                    } else if ("BATTLE".equals(type)) {
                        // 先日志透传；若需要我可以把海战棋的客户端裁决完善
                        log.println("[CLIENT] 收到海战棋事件: " + jo);
                    } else if ("HELLO".equals(type)) {
                        log.println("[CLIENT] 收到服务端 HELLO: " + jo);
                    } else {
                        log.println("[CLIENT] 收到未识别消息: " + jo);
                    }
                }
            } catch (SocketTimeoutException te) {
                log.println("[CLIENT] read timeout: " + te.getMessage());
            } catch (EOFException eof) {
                log.println("[CLIENT] peer closed: " + eof.getMessage());
            } catch (Exception e) {
                log.println("[CLIENT] exception: " + e.getClass().getName() + ": " + String.valueOf(e.getMessage()));
                StringWriter sw = new StringWriter();
                e.printStackTrace(new PrintWriter(sw));
                log.println(sw.toString());
            } finally {
                running = false;
                try { if (br != null) br.close(); } catch (Exception ignore) {}
                try { if (out != null) out.close(); } catch (Exception ignore) {}
                try { if (socket != null) socket.close(); } catch (Exception ignore) {}
                log.println("[CLIENT] read loop end, connection closed.");
            }
        }, "client-read-loop");
        readThread.setDaemon(true);
        readThread.start();
    }

    /** 主动关闭连接（可供 UI 调用） */
    public void close() {
        running = false;
        try { if (br != null) br.close(); } catch (Exception ignore) {}
        try { if (out != null) out.close(); } catch (Exception ignore) {}
        try { if (socket != null) socket.close(); } catch (Exception ignore) {}
    }

    // ========= 发送 =========
    public void sendJson(JSONObject jo) throws IOException {
        if (out == null) throw new IOException("尚未连接");
        Proto.sendJSON(out, jo);
    }

    @Override
    public void sendMove(int x, int y, int turn, String hash) throws IOException {
        if (out == null) throw new IOException("尚未连接服务器");
        Proto.sendJSON(out, Proto.move(x, y, turn, hash));
    }
}
