package com.easy.net;

import com.easy.ui.ConsoleSink;
import com.easy.ui.MoveListener;
import com.easy.ui.NetEventListener;
import org.json.JSONObject;

import java.io.*;
import java.net.*;

public class NetClient implements com.easy.ui.MoveSender {
    private final ConsoleSink log;
    private final MoveListener listener;
    private final NetEventListener events;

    private Socket s;
    private BufferedReader br;
    private OutputStream out;

    public NetClient(ConsoleSink log, MoveListener listener){ this(log, listener, null); }
    public NetClient(ConsoleSink log, MoveListener listener, NetEventListener events){
        this.log = log; this.listener = listener; this.events = events;
    }

    public void connect(String inviteCode) throws Exception {
        InviteCodec.Endpoint ep = InviteCodec.parse(inviteCode==null? null : inviteCode.replaceAll("\\s+",""));
        log.println("[CLIENT] 解析邀请码 -> " + ep.ip + ":" + ep.port);
        s = new Socket();
        s.setTcpNoDelay(true);
        log.println("[CLIENT] connecting to " + ep.ip + ":" + ep.port + " ...");
        s.connect(new InetSocketAddress(ep.ip, ep.port), 8000);
        br = new BufferedReader(new InputStreamReader(s.getInputStream(),"UTF-8"));
        out = s.getOutputStream();
        log.println("[CLIENT] connected. local="+s.getLocalAddress()+":"+s.getLocalPort());

        // 发送 HELLO
        JSONObject hello = Proto.hello("client");
        log.println("[CLIENT] sending HELLO: " + hello);
        Proto.sendJSON(out, hello);

        // 读对端 HELLO
        try {
            JSONObject h = Proto.readJSON(br);
            log.println("[CLIENT] HELLO read: " + h);
        } catch (Exception ex){
            log.println("[CLIENT] HELLO read error: " + ex);
        }

        new Thread(this::loopRead, "client-read-loop").start();
    }

    private void loopRead(){
        try {
            log.println("[CLIENT] entering read loop...");
            JSONObject jo;
            while ((jo = Proto.readJSON(br)) != null){
                String t = jo.optString("type","");
                if ("MOVE".equals(t)){
                    if (jo.has("fx")){
                        int fx=jo.getInt("fx"), fy=jo.getInt("fy");
                        int x=jo.getInt("x"), y=jo.getInt("y");
                        if (listener instanceof com.easy.ui.BoardCanvas canvas){
                            canvas.onOpponentMoveFxFy(fx,fy,x,y);
                        }
                    }else{
                        listener.onOpponentMove(jo.getInt("x"), jo.getInt("y"));
                    }
                } else if ("GAME".equals(t)){
                    String cmd = jo.optString("cmd","");
                    if ("select".equals(cmd) && events!=null){
                        events.onGameSelected(com.easy.game.GameType.from(jo.getString("game")), jo.optString("starter","host"));
                    } else if ("suggest".equals(cmd) && events!=null){
                        events.onGameSuggested(com.easy.game.GameType.from(jo.getString("game")));
                    }
                } else if ("BATTLE".equals(t)){
                    String cmd = jo.optString("cmd","");
                    if ("fire".equals(cmd)){
                        int x=jo.getInt("x"), y=jo.getInt("y");
                        listener.onOpponentMove(x,y); // 对方打我
                    }
                } else if ("HELLO".equals(t)){
                    // ignore
                } else {
                    log.println("[CLIENT] recv: " + jo);
                }
            }
            log.println("[CLIENT] read loop end, connection closed.");
        } catch (Exception e){
            log.println("[CLIENT] read error: " + e.getMessage());
        }
    }

    public void sendJson(JSONObject jo) throws IOException {
        if (out==null) throw new IOException("尚未连接");
        Proto.sendJSON(out, jo);
    }

    @Override
    public void sendMove(int x,int y,int turn,String hash) throws IOException {
        if (out==null) throw new IOException("尚未连接");
        Proto.sendJSON(out, Proto.move(x,y,turn,hash));
    }
}
