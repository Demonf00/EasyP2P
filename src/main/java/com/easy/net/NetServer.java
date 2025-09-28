package com.easy.net;

import com.easy.ui.ConsoleSink;
import com.easy.ui.MoveListener;
import com.easy.ui.NetEventListener;
// 关键：明确 import UI 包里的 MoveSender 接口
import com.easy.ui.MoveSender;

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;

public class NetServer implements MoveSender {   // 关键：实现 com.easy.ui.MoveSender
    private final int port;
    private final ConsoleSink log;
    private final MoveListener moveListener;
    private final NetEventListener events;

    private volatile boolean running = false;
    private ServerSocket serverSocket;
    private Socket socket;
    private ObjectOutputStream out;
    private ObjectInputStream  in;

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
            while (running) {                      // ← 保持 accept 循环，客户端断了继续等下一个
                try {
                    socket = serverSocket.accept();
                    out = new ObjectOutputStream(socket.getOutputStream());
                    in  = new ObjectInputStream(socket.getInputStream());
                    log.println("客户端已连接: " + socket.getInetAddress().getHostAddress());
                    Proto.sendJSON(out, Proto.hello("server"));

                    // 读循环
                    while (running && !socket.isClosed()) {
                        Object obj = in.readObject();
                        org.json.JSONObject jo = (obj instanceof org.json.JSONObject)
                                ? (org.json.JSONObject) obj
                                : new org.json.JSONObject(String.valueOf(obj));

                        String type = jo.optString("type","");
                        if ("MOVE".equals(type)) {
                            if (moveListener != null) moveListener.onOpponentMove(jo.getInt("x"), jo.getInt("y"));
                        } else if ("GAME".equals(type)) {
                            String cmd = jo.optString("cmd","");
                            String g = jo.optString("game","GOMOKU");
                            String starter = jo.optString("starter","host");
                            if (events != null) {
                                if ("select".equals(cmd)) events.onGameSelected(com.easy.game.GameType.from(g), starter);
                                else if ("suggest".equals(cmd)) events.onGameSuggested(com.easy.game.GameType.from(g));
                            }
                            log.println("收到事件: " + jo.toString());
                        } else if ("BATTLE".equals(type)) {
                            log.println("收到事件: " + jo.toString());
                        }
                    }
                } catch (EOFException eof) {
                    log.println("对端已正常断开");
                } catch (Exception e) {
                    // 打出异常类型 + 栈，方便你定位
                    log.println("服务器异常: " + e.getClass().getName() + ": " + String.valueOf(e.getMessage()));
                    StringWriter sw = new StringWriter();
                    e.printStackTrace(new PrintWriter(sw));
                    log.println(sw.toString());
                } finally {
                    try { if (in!=null) in.close(); } catch (Exception ignore){}
                    try { if (out!=null) out.close(); } catch (Exception ignore){}
                    try { if (socket!=null) socket.close(); } catch (Exception ignore){}
                    // 不要在这里把 serverSocket 关掉，这样还能继续 accept
                }
                // 回到 while(running) 再次等待下一个客户端
            }

            // 真正停止服务器
            try { if (serverSocket!=null) serverSocket.close(); } catch (Exception ignore){}
            if (upnpEnabled) {
                try { com.easy.net.UpnpHelper.closeTcp(port); log.println("已回收 UPnP 端口映射: " + port); } catch (Exception ignore){}
            }
            log.println("服务器已关闭");
        }, "easy-netserver");
        t.setDaemon(true);
        t.start();
    }

    public void stopServer() {
        running = false;
        try { if (socket!=null) socket.close(); } catch (Exception ignore){}
        try { if (serverSocket!=null) serverSocket.close(); } catch (Exception ignore){}
    }

    public void closeUpnpIfAny(){
        if (upnpEnabled) { try { com.easy.net.UpnpHelper.closeTcp(port); } catch (Exception ignore){} }
    }

    @Override public void sendMove(int x, int y, int turn, String hash) throws IOException {
        if (out == null) throw new IOException("尚未连接");
        Proto.sendJSON(out, Proto.move(x,y,turn,hash));
    }
    public void sendJson(org.json.JSONObject jo) throws IOException {
        if (out == null) throw new IOException("尚未连接");
        Proto.sendJSON(out, jo);
    }
}
