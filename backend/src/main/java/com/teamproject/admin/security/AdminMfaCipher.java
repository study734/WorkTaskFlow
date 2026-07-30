package com.teamproject.admin.security;

import com.teamproject.common.exception.ApplicationException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;

@Component
public class AdminMfaCipher {
    private final String encodedKey;
    private final SecureRandom random = new SecureRandom();
    public AdminMfaCipher(@Value("${app.admin.mfa-encryption-key-base64:}") String encodedKey) {
        this.encodedKey = encodedKey;
    }
    public boolean configured() {
        try { return key().length == 32; } catch (RuntimeException exception) { return false; }
    }
    public String encrypt(String value) {
        try {
            byte[] iv = new byte[12]; random.nextBytes(iv);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(key(), "AES"), new GCMParameterSpec(128, iv));
            byte[] encrypted = cipher.doFinal(value.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(ByteBuffer.allocate(iv.length + encrypted.length)
                    .put(iv).put(encrypted).array());
        } catch (Exception exception) { throw configurationError(); }
    }
    public String decrypt(String value) {
        try {
            byte[] combined = Base64.getDecoder().decode(value);
            ByteBuffer buffer = ByteBuffer.wrap(combined);
            byte[] iv = new byte[12]; buffer.get(iv);
            byte[] encrypted = new byte[buffer.remaining()]; buffer.get(encrypted);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(key(), "AES"), new GCMParameterSpec(128, iv));
            return new String(cipher.doFinal(encrypted), StandardCharsets.UTF_8);
        } catch (Exception exception) { throw configurationError(); }
    }
    private byte[] key() {
        byte[] value = Base64.getDecoder().decode(encodedKey);
        if (value.length != 32) throw configurationError();
        return value;
    }
    private ApplicationException configurationError() {
        return new ApplicationException("ADMIN_MFA_ENCRYPTION_NOT_CONFIGURED", HttpStatus.SERVICE_UNAVAILABLE,
                "관리자 MFA 암호화 설정이 완료되지 않았습니다.");
    }
}
