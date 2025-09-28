package com.easy.net;

import com.easy.ui.ConsoleSink;
import com.easy.ui.MoveListener;
import com.easy.ui.MoveSender;
import org.json.JSONObject;

import java.io.*;
import java.net.*;

public class NetClient implements MoveSender {
    private final ConsoleSink log;
    private final MoveListener listener;

    private Socket s;
    private BufferedReader br;
    private OutputStream out;

    public NetClient(ConsoleSink log, MoveListener listener) {
        this.log = log;
        this.listener = listener;
    }

    public void connect(String inviteCode) throws Exception {
        InviteCodec.Endpoint ep = InviteCodec.parse(inviteCode);
        log.println("连接到 " + ep.ip + ":" + ep.port);
        s = new Socket();
        s.setTcpNoDelay(true);
        s.connect(new InetSocketAddress(ep.ip, ep.port), 5000);
        br = new BufferedReader(new InputStreamReader(s.getInputStream(), "UTF-8"));
        out = s.getOutputStream();
        JSONObject hello = Proto.readJSON(br);
        log.println("握手: " + hello);
        loopReadAsync();
    }

    private void loopReadAsync() {
        new Thread(() -> {
            try {
                JSONObject jo;
                while ((jo = Proto.readJSON(br)) != null) {
                    String type = jo.optString("type", "");
                    if ("ACK".equals(type)) {
                        log.println("收到 ACK");
                    } else if ("MOVE".equals(type)) {
                        int x = jo.getInt("x"), y = jo.getInt("y");
                        log.println("收到对方落子 x=" + x + " y=" + y);
                        if (listener != null) listener.onOpponentMove(x, y);
                    } else {
                        log.println("收到: " + jo.toString());
                    }
                }
            } catch (Exception e) {
                log.println("读循环结束: " + e.getMessage());
            }
        }, "client-read-loop").start();
    }

    @Override
    public void sendMove(int x, int y, int turn, String hash) throws IOException {
        if (out == null) throw new IOException("尚未连接服务器");
        Proto.sendJSON(out, Proto.move(x, y, turn, hash));
    }
}
