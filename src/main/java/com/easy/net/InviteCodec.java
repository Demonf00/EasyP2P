package com.easy.net;

import com.easy.util.AES256;
import java.time.Instant;
import java.util.*;

public class InviteCodec {
    // 32-byte key; replace in prod (env/config). DO NOT commit secrets in real apps.
    private static final byte[] KEY = "0123456789abcdef0123456789abcdef".getBytes();

    public static String gen(String ip, int port) throws Exception {
        long ts = Instant.now().getEpochSecond();
        String nonce = UUID.randomUUID().toString().replaceAll("-", "").substring(0, 16);
        String plain = String.format("v=1|ip=%s|port=%d|ts=%d|nonce=%s", ip, port, ts, nonce);
        return AES256.encrypt(plain, KEY);
    }


public static Endpoint parse(String code) throws Exception {
    if (code == null) throw new IllegalArgumentException("empty invite");
    // Remove all whitespace/newlines users may paste
    code = code.replaceAll("\s+", "");
    String s = AES256.decrypt(code, KEY);
    // Normalize payload spaces just in case
    s = s.trim();
    String[] parts = s.split("\|");
    String ip = null; int port = -1;
    for (String p: parts) {
        String[] kv = p.split("=", 2);
        if (kv.length != 2) continue;
        String k = kv[0].trim(); String v = kv[1].trim();
        if ("ip".equals(k)) ip = v;
        if ("port".equals(k)) {
            try { port = Integer.parseInt(v); } catch (Exception ignore) {}
        }
    }
    if (ip == null || port <= 0) throw new IllegalArgumentException("bad invite payload");
    return new Endpoint(ip, port);
}

        if (ip == null || port <= 0) throw new IllegalArgumentException("bad invite payload");
        return new Endpoint(ip, port);
    }

    public static class Endpoint {
        public final String ip;
        public final int port;
        public Endpoint(String ip, int port){ this.ip = ip; this.port = port; }
    }
}
