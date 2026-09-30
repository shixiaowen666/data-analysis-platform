package com.senses.permission.util;

import com.senses.permission.config.KeyPairHolder;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import java.nio.charset.StandardCharsets;
import java.security.PrivateKey;
import java.util.Base64;

@Slf4j
@Component
@RequiredArgsConstructor
public class CryptoUtils {

    private final KeyPairHolder keyPairHolder;

    private static final long TIME_WINDOW_MS = 5 * 60 * 1000;

    /**
     * RSA 解密密码，校验时间戳防重放。
     * 密文格式：RSA(Base64(plainPassword|timestamp))
     */
    public String decryptPassword(String ciphertext) throws Exception {
        PrivateKey privateKey = keyPairHolder.getPrivateKey();

        Cipher cipher = Cipher.getInstance("RSA/ECB/PKCS1Padding");
        cipher.init(Cipher.DECRYPT_MODE, privateKey);
        byte[] plainBytes = cipher.doFinal(Base64.getDecoder().decode(ciphertext));
        String plaintext = new String(plainBytes, StandardCharsets.UTF_8);

        int sepIdx = plaintext.lastIndexOf("|");
        if (sepIdx == -1) {
            throw new IllegalArgumentException("密文格式错误");
        }

        String password = plaintext.substring(0, sepIdx);
        long timestamp = Long.parseLong(plaintext.substring(sepIdx + 1));

        long now = System.currentTimeMillis();
        if (Math.abs(now - timestamp) > TIME_WINDOW_MS) {
            log.warn("登录请求时间戳过期，时间差: {}ms", Math.abs(now - timestamp));
            throw new IllegalArgumentException("请求已过期，请刷新页面重试");
        }

        return password;
    }
}
