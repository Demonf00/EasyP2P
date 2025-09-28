package com.easy.util;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.security.SecureRandom;
import java.util.Base64;
import java.nio.charset.StandardCharsets;

public class AES256 {
    private static final String ALG = "AES/CBC/PKCS5Padding";

    public static String encrypt(String plaintext, byte[] key32) throws Exception {
        SecureRandom rnd = new SecureRandom();
        byte[] iv = new byte[16];
        rnd.nextBytes(iv);
        IvParameterSpec ivspec = new IvParameterSpec(iv);
        SecretKey key = new SecretKeySpec(key32, "AES");
        Cipher c = Cipher.getInstance(ALG);
        c.init(Cipher.ENCRYPT_MODE, key, ivspec);
        byte[] enc = c.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));
        byte[] out = new byte[iv.length + enc.length];
        System.arraycopy(iv,0,out,0,iv.length);
        System.arraycopy(enc,0,out,iv.length,enc.length);
        return Base64.getEncoder().encodeToString(out);
    }

    public static String decrypt(String b64, byte[] key32) throws Exception {
        byte[] data = Base64.getDecoder().decode(b64);
        if (data.length < 17) throw new IllegalArgumentException("cipher too short");
        byte[] iv = new byte[16];
        System.arraycopy(data,0,iv,0,16);
        IvParameterSpec ivspec = new IvParameterSpec(iv);
        SecretKey key = new SecretKeySpec(key32, "AES");
        Cipher c = Cipher.getInstance(ALG);
        c.init(Cipher.DECRYPT_MODE, key, ivspec);
        byte[] dec = c.doFinal(data, 16, data.length - 16);
        return new String(dec, StandardCharsets.UTF_8);
    }
}
