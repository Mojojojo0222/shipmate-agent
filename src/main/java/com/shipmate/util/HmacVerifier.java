package com.shipmate.util;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.security.MessageDigest;

public class HmacVerifier {

    public static boolean verify(String secret, String body, String signatureHeader) {
        if (signatureHeader == null || !signatureHeader.startsWith("sha256=")) {
            return false;
        }
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(), "HmacSHA256"));
            byte[] digest = mac.doFinal(body.getBytes());
            StringBuilder sb = new StringBuilder();
            for (byte b : digest) {
                sb.append(String.format("%02x", b));
            }
            return MessageDigest.isEqual(sb.toString().getBytes(), signatureHeader.substring(7).getBytes());
        } catch (Exception e) {
            return false;
        }
    }
}
