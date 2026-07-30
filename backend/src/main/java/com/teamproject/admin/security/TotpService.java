package com.teamproject.admin.security;

import org.springframework.stereotype.Component;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.security.MessageDigest;
import java.security.SecureRandom;

@Component
public class TotpService {
    private static final char[] BASE32 = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567".toCharArray();
    private final SecureRandom random = new SecureRandom();
    public String secret() {
        byte[] value = new byte[20]; random.nextBytes(value);
        return encodeBase32(value);
    }
    public boolean verify(String secret, String rawCode) {
        if (rawCode == null) return false;
        String code = rawCode.replaceAll("[^0-9]", "");
        if (code.length() != 6) return false;
        long step = System.currentTimeMillis() / 30_000L;
        for (long candidate = step - 1; candidate <= step + 1; candidate++) {
            if (MessageDigest.isEqual(code.getBytes(java.nio.charset.StandardCharsets.US_ASCII),
                    generate(secret, candidate).getBytes(java.nio.charset.StandardCharsets.US_ASCII))) return true;
        }
        return false;
    }
    String generate(String secret, long counter) {
        try {
            Mac mac = Mac.getInstance("HmacSHA1");
            mac.init(new SecretKeySpec(decodeBase32(secret), "HmacSHA1"));
            byte[] hash = mac.doFinal(ByteBuffer.allocate(8).putLong(counter).array());
            int offset = hash[hash.length - 1] & 0x0f;
            int binary = ((hash[offset] & 0x7f) << 24) | ((hash[offset + 1] & 0xff) << 16)
                    | ((hash[offset + 2] & 0xff) << 8) | (hash[offset + 3] & 0xff);
            return String.format("%06d", binary % 1_000_000);
        } catch (Exception exception) { throw new IllegalStateException("TOTP calculation failed.", exception); }
    }
    private String encodeBase32(byte[] input) {
        StringBuilder result = new StringBuilder();
        int buffer = 0, bits = 0;
        for (byte value : input) {
            buffer = (buffer << 8) | (value & 0xff); bits += 8;
            while (bits >= 5) { result.append(BASE32[(buffer >> (bits - 5)) & 31]); bits -= 5; }
        }
        if (bits > 0) result.append(BASE32[(buffer << (5 - bits)) & 31]);
        return result.toString();
    }
    private byte[] decodeBase32(String input) {
        java.io.ByteArrayOutputStream output = new java.io.ByteArrayOutputStream();
        int buffer = 0, bits = 0;
        for (char character : input.toUpperCase(java.util.Locale.ROOT).toCharArray()) {
            int value = character >= 'A' && character <= 'Z' ? character - 'A'
                    : character >= '2' && character <= '7' ? character - '2' + 26 : -1;
            if (value < 0) continue;
            buffer = (buffer << 5) | value; bits += 5;
            if (bits >= 8) { output.write((buffer >> (bits - 8)) & 0xff); bits -= 8; }
        }
        return output.toByteArray();
    }
}
