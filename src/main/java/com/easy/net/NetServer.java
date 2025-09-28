package com.easy.net;

import com.easy.ui.ConsoleSink;
import com.easy.ui.MoveListener;
import com.easy.ui.NetEventListener;
import com.easy.ui.MoveSender;
import org.json.JSONObject;

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketTimeoutException;

/** 纯文本 JSON 协议的单连接服务器（与客户端一致） */
public class NetServer implements MoveSender {

    private final int port;
    private final ConsoleSink log;
    private final MoveListener moveListener;
    private final NetEventListener events;

    private volatile boolean running = false;
    private ServerSocket serverSocket;

    private Socket socket;
    private BufferedReader br;
    private OutputStream out;

    private boolean upnpEnabled = false;
    public void setUpnpEnabled(boolean enabled){ this.upnpEnabled = enabled; }

    public NetServer(int port, ConsoleSink log, MoveListener ml, NetEventListener ev) {
        this.port = port; this.log = log; this.moveListener = ml; this.events = ev;
    }

    public void start() throws IOException {
        serverSocket = new ServerSocket(port);
        running = true;

        Thread t = new Thread(() -> {
            log.println("服务器监听端口: " + port + "，等待客户端连接...");
            while (running) {
                try {
                    socket = serverSocket.accept();
                    socket.setTcpNoDelay(true);
                    socket.setKeepAlive(true);
                    socket.setSoTimeout(120_000); // 读超时，避免读阻塞
                    log.println("[SERVER] accepted from " + socket.getRemoteSocketAddress());

                    br  = new BufferedReader(new InputStreamReader(socket.getInputStream(), "UTF-8"));
                    out = socket.getOutputStream();

                    // 发送 HELLO
                    log.println("[SERVER] sending HELLO...");
                    Proto.sendJSON(out, Proto.hello("server"));
                    log.println("[SERVER] HELLO sent.");

                    // 进入读循环
                    log.println("[SERVER] entering read loop...");
                    while (running && !socket.isClosed()) {
                        JSONObject jo = Proto.readJSON(br); // null 表示对端关闭
                        if (jo == null) {
                            log.println("[SERVER] read JSON -> null，对端关闭？");
                            break;
                        }
                        log.println("[SERVER] received: " + jo);

                        String type = jo.optString("type","");
                        if ("MOVE".equals(type)) {
                            int x = jo.getInt("x"), y = jo.getInt("y");
                            if (moveListener != null) moveListener.onOpponentMove(x, y);
                        } else if ("GAME".equals(type)) {
                            String cmd = jo.optString("cmd","");
                            String g = jo.optString("game","GOMOKU");
                            String starter = jo.optString("starter","host");
                            if (events != null) {
                                if ("select".equals(cmd)) events.onGameSelected(com.easy.game.GameType.from(g), starter);
                                else if ("suggest".equals(cmd)) events.onGameSuggested(com.easy.game.GameType.from(g));
                                else if ("reset".equals(cmd))  events.onGameSelected(com.easy.game.GameType.from(g), "host");
                            }
                        } else if ("HELLO".equals(type)) {
                            // 对端 HELLO
                        } else if ("BATTLE".equals(type)) {
                            // 先日志透传；需要的话可在此做服务端裁决
                        } else if ("ACK".equals(type)) {
                            // 可忽略
                        } else {
                            log.println("[SERVER] 未识别消息: " + jo);
                        }
                    }
                } catch (SocketTimeoutException te) {
                    log.println("[SERVER] read timeout: " + te.getMessage());
                } catch (EOFException eof) {
                    log.println("[SERVER] peer closed: " + eof.getMessage());
                } catch (Exception e) {
                    log.println("[SERVER] exception: " + e.getClass().getName() + ": " + String.valueOf(e.getMessage()));
                    StringWriter sw = new StringWriter();
                    e.printStackTrace(new PrintWriter(sw));
                    log.println(sw.toString());
                } finally {
                    try { if (br != null) br.close(); } catch (Exception ignore){}
                    try { if (out != null) out.close(); } catch (Exception ignore){}
                    try { if (socket != null) socket.close(); } catch (Exception ignore){}
                    // 不关闭 serverSocket；继续下一轮 accept
                }
            }

            // 停服与回收 UPnP
            try { if (serverSocket != null) serverSocket.close(); } catch (Exception ignore){}
            if (upnpEnabled) {
                try { UpnpHelper.closeTcp(port); log.println("已回收 UPnP 端口映射: " + port); } catch (Exception ignore){}
            }
            log.println("服务器已关闭");
        }, "easy-netserver");
        t.setDaemon(true);
        t.start();
    }

    public void stopServer(){
        running = false;
        try { if (socket != null) socket.close(); } catch (Exception ignore){}
        try { if (serverSocket != null) serverSocket.close(); } catch (Exception ignore){}
    }

    public void closeUpnpIfAny(){
        if (upnpEnabled) { try { UpnpHelper.closeTcp(port); } catch (Exception ignore){} }
    }

    // ===== MoveSender 实现（发 JSON）=====
    @Override
    public void sendMove(int x, int y, int turn, String hash) throws IOException {
        if (out == null) throw new IOException("尚未连接");
        Proto.sendJSON(out, Proto.move(x, y, turn, hash));
    }

    public void sendJson(JSONObject jo) throws IOException {
        if (out == null) throw new IOException("尚未连接");
        Proto.sendJSON(out, jo);
    }
}
