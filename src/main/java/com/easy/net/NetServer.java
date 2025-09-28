package com.easy.net;

import com.easy.ui.ConsoleSink;
import com.easy.ui.MoveListener;
import com.easy.ui.NetEventListener;
import org.json.JSONObject;

import java.io.*;
import java.net.*;
import java.util.concurrent.atomic.AtomicBoolean;

public class NetServer implements com.easy.ui.MoveSender {

    private final int port;
    private final ConsoleSink log;
    private final MoveListener listener;
    private final NetEventListener events;

    private ServerSocket ss;
    private Socket s;
    private BufferedReader br;
    private OutputStream out;

    // ===== UPnP 开关与状态 =====
    private volatile boolean upnpEnabled = true;  // 默认启用（由 UI 切公网时打开）
    private volatile boolean upnpMapped  = false; // 是否已映射成功
    private volatile int     mappedPort  = -1;

    public NetServer(int port, ConsoleSink log, MoveListener listener, NetEventListener events){
        this.port = port;
        this.log = log;
        this.listener = listener;
        this.events = events;
    }

    public void start() throws IOException {
        ss = new ServerSocket();
        ss.setReuseAddress(true);
        ss.bind(new InetSocketAddress(port));
        log.println("服务器监听端口: " + port + "，等待客户端连接…");

        // 开启 UPnP（如果用户选择公网）
        if (upnpEnabled) {
            tryOpenUpnp(port);
        }

        new Thread(this::acceptLoop, "server-accept").start();
    }

    private void acceptLoop() {
        try {
            s = ss.accept();
            log.println("客户端已连接: " + s.getRemoteSocketAddress());
            s.setTcpNoDelay(true);

            out = s.getOutputStream();
            br  = new BufferedReader(new InputStreamReader(s.getInputStream(), "UTF-8"));

            // 先发 HELLO
            Proto.sendJSON(out, Proto.hello("server"));
            log.println("[SERVER] HELLO sent.");

            // 读循环
            readLoop();
        } catch (Exception e) {
            log.println("服务器异常: " + e.getMessage());
        } finally {
            close();
        }
    }

    private void readLoop() {
        try {
            log.println("[SERVER] entering read loop...");
            JSONObject jo;
            while ((jo = Proto.readJSON(br)) != null) {
                String t = jo.optString("type", "");
                log.println("[SERVER] received: " + jo.toString());

                if ("HELLO".equals(t)) {
                    // ignore
                } else if ("MOVE".equals(t)) {
                    int x = jo.optInt("x"), y = jo.optInt("y");
                    if (listener != null) listener.onOpponentMove(x, y);
                    // 回 ACK
                    Proto.sendJSON(out, Proto.ack());
                } else if ("MOVE_FXFY".equals(t)) {
                    int fx = jo.optInt("fx"), fy = jo.optInt("fy");
                    int tx = jo.optInt("x"),  ty = jo.optInt("y");
                    if (listener instanceof com.easy.ui.BoardCanvas bc) {
                        bc.onOpponentMoveFxFy(fx, fy, tx, ty);
                    } else if (listener != null) {
                        // 退化为单点
                        listener.onOpponentMove(tx, ty);
                    }
                    Proto.sendJSON(out, Proto.ack());
                } else if ("GAME".equals(t)) {
                    String cmd = jo.optString("cmd", "");
                    String g   = jo.optString("game", "GOMOKU");
                    String starter = jo.optString("starter", "host");
                    if ("select".equals(cmd)) {
                        if (events != null) events.onGameSelected(com.easy.game.GameType.from(g), starter);
                    } else if ("suggest".equals(cmd)) {
                        if (events != null) events.onGameSuggested(com.easy.game.GameType.from(g));
                    } else if ("reset".equals(cmd)) {
                        // 如果你要“重开当前棋局”，这里触发 UI 重置
                        if (events != null) events.onGameSelected(com.easy.game.GameType.from(g), "host");
                    }
                }
            }
            log.println("[SERVER] read loop end, peer closed.");
        } catch (SocketTimeoutException e) {
            log.println("[SERVER] read timeout: " + e.getMessage());
        } catch (Exception e) {
            log.println("[SERVER] read error: " + e.getMessage());
        }
    }

    // ====== 供 UI/外部调用 ======
    public void sendJson(JSONObject jo) throws IOException {
        if (out == null) throw new IOException("尚未建立连接");
        Proto.sendJSON(out, jo);
    }

    @Override
    public void sendMove(int x, int y, int turn, String hash) throws IOException {
        if (out == null) throw new IOException("尚未建立连接");
        Proto.sendJSON(out, Proto.move(x, y, turn, hash));
    }

    public void close() {
        try { if (br != null)  br.close(); } catch (Exception ignore) {}
        try { if (out != null) out.close(); } catch (Exception ignore) {}
        try { if (s != null)    s.close(); } catch (Exception ignore) {}
        try { if (ss != null)  ss.close(); } catch (Exception ignore) {}
        closeUpnpIfAny();
        log.println("服务器已关闭");
    }

    // ===== UPnP 相关 =====

    /** UI 可调用：打开/关闭 UPnP 功能（仅影响后续 start/映射行为） */
    public void setUpnpEnabled(boolean enabled) {
        this.upnpEnabled = enabled;
        log.println("UPnP 已" + (enabled ? "启用" : "关闭"));
    }

    /** UI 可调用：显式撤销映射（或在 close() 时自动调用） */
    public void closeUpnpIfAny() {
        if (!upnpMapped) return;
        try {
            // 如果存在 simtechdata 的库，优先使用
            Class<?> cls = Class.forName("com.simtechdata.waifupnp.UPnP");
            // closePortTCP(int)
            cls.getMethod("closePortTCP", int.class).invoke(null, mappedPort);
            log.println("已回收 UPnP 端口映射: " + mappedPort);
        } catch (ClassNotFoundException cnf) {
            // 没有库就忽略（或者在这里接入你自己的 miniupnp）
            log.println("未检测到 waifupnp 库，跳过回收映射（可忽略）。");
        } catch (Exception e) {
            log.println("回收 UPnP 端口映射失败: " + e.getMessage());
        } finally {
            upnpMapped = false;
            mappedPort = -1;
        }
    }

    /** 根据端口尝试建立 UPnP 映射（库可选存在） */
    private void tryOpenUpnp(int port) {
        try {
            Class<?> cls = Class.forName("com.simtechdata.waifupnp.UPnP");
            boolean ok = (boolean) cls.getMethod("openPortTCP", int.class).invoke(null, port);
            if (ok) {
                upnpMapped = true;
                mappedPort = port;
                log.println("UPnP 端口映射成功: " + port + "/TCP");
            } else {
                log.println("UPnP 端口映射失败（路由器不支持或被禁用）");
            }
        } catch (ClassNotFoundException cnf) {
            log.println("未检测到 waifupnp 库，跳过 UPnP 自动映射（可忽略）。");
        } catch (Exception e) {
            log.println("UPnP 映射异常: " + e.getMessage());
        }
    }
}
