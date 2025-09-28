package com.easy.net;

import com.easy.util.AES256;
import java.time.Instant;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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
    String original = code;
    code = code.replaceAll("\s+", "");

    String s;
    try {
        s = AES256.decrypt(code, KEY);
    } catch (Exception e) {
        String msg = e.getClass().getSimpleName() + (e.getMessage() == null ? "" : (": " + e.getMessage()));
        throw new IllegalArgumentException("decrypt failed (" + msg + "). Hint: ensure full single-line code.");
    }
    if (s == null) throw new IllegalArgumentException("decrypted payload is null");

    String payload = s.trim();
    // Robustly extract using regex to avoid split pitfalls
    Pattern ipPat   = Pattern.compile("(^|\\|)ip=([^|]+)($|\\|)");
    Pattern portPat = Pattern.compile("(^|\\|)port=([0-9]{1,5})($|\\|)");


    Matcher m1 = ipPat.matcher(payload);
    Matcher m2 = portPat.matcher(payload);

    String ip = null;
    Integer port = null;

    if (m1.find()) ip = m1.group(2).trim();
    if (m2.find()) {
        try { port = Integer.parseInt(m2.group(2)); } catch (NumberFormatException ignore) {}
    }

    if (ip == null || ip.isEmpty()) {
        throw new IllegalArgumentException("missing ip in payload (payload='" + payload + "', code='" + original + "')");
    }
    if (port == null || port <= 0 || port > 65535) {
        throw new IllegalArgumentException("missing/invalid port in payload (payload='" + payload + "', code='" + original + "')");
    }
    return new Endpoint(ip, port);
}
    public static class Endpoint {
        public final String ip;
        public final int port;
        public Endpoint(String ip, int port){ this.ip = ip; this.port = port; }
    }
}
