package com.easy.net;

import org.json.JSONObject;

public class Proto {

    public static JSONObject hello(String role){
        return new JSONObject().put("type","HELLO").put("role",role);
    }

    // 传统单点 MOVE（仍保留给五子棋/黑白棋）
    public static JSONObject move(int x,int y,int turn,String hash){
        return new JSONObject().put("type","MOVE").put("x",x).put("y",y).put("turn",turn).put("hash",hash);
    }

    // 新：带起点/终点的 MOVE（用于 Chess/Checkers）
    public static JSONObject moveFxFy(int fx,int fy,int x,int y){
        return new JSONObject().put("type","MOVE").put("fx",fx).put("fy",fy).put("x",x).put("y",y);
    }

    // GAME：select/suggest/reset
    public static JSONObject gameSelect(String game, String starter){
        return new JSONObject().put("type","GAME").put("cmd","select").put("game",game).put("starter",starter);
    }
    public static JSONObject gameSuggest(String game){
        return new JSONObject().put("type","GAME").put("cmd","suggest").put("game",game);
    }

    // Battleship
    public static JSONObject battleFire(int x,int y){
        return new JSONObject().put("type","BATTLE").put("cmd","fire").put("x",x).put("y",y);
    }

    // I/O
    public static void sendJSON(java.io.OutputStream out, JSONObject jo) throws java.io.IOException {
        byte[] b = (jo.toString()+"\n").getBytes(java.nio.charset.StandardCharsets.UTF_8);
        out.write(b); out.flush();
    }
    public static JSONObject readJSON(java.io.BufferedReader br) throws java.io.IOException {
        String s = br.readLine();
        if (s==null) return null;
        return new JSONObject(s);
    }
    public static org.json.JSONObject resetRound() {
        return new org.json.JSONObject()
                .put("type", "GAME")
                .put("cmd", "reset");
    }
    public static org.json.JSONObject ack() {
        // 轻量 ACK：服务端/客户端收到后可忽略或仅做日志
        return new org.json.JSONObject().put("type", "ACK");
    }
}
