package com.easy.net;

import org.json.JSONObject;
import java.io.*;
import java.nio.charset.StandardCharsets;

public class Proto {
    public static void sendJSON(OutputStream out, JSONObject jo) throws IOException {
        byte[] b = (jo.toString() + "\n").getBytes(StandardCharsets.UTF_8);
        out.write(b);
        out.flush();
    }

    public static JSONObject readJSON(BufferedReader br) throws IOException {
        String line = br.readLine();
        if (line == null) return null;
        return new JSONObject(line);
    }

    public static JSONObject hello(String role){
        return new JSONObject().put("type","HELLO").put("role", role);
    }
    public static JSONObject ack(){
        return new JSONObject().put("type","ACK");
    }
    public static JSONObject move(int x, int y, int turn, String hash){
        return new JSONObject().put("type","MOVE").put("x",x).put("y",y).put("turn",turn).put("hash",hash);
    }
}
