package com.wm.semantic.common.util;

import lombok.extern.slf4j.Slf4j;

import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

/**
 * AES 对称加解密工具，用于数据源密码的存储和读取。
 *
 * 密钥通过 {@link #setKey(String)} 注入（推荐从 application.yml 读取）。
 * 未配置密钥时，明文原样返回（仅用于开发/兼容过渡场景）。
 */
@Slf4j
public final class PasswordUtil {

    private static volatile String aesKey;

    private PasswordUtil() {}

    public static void setKey(String key) {
        aesKey = key;
    }

    public static String encrypt(String plainText) {
        if (plainText == null || plainText.isEmpty()) return plainText;
        if (aesKey == null || aesKey.isEmpty()) {
            log.warn("[PasswordUtil] AES密钥未配置，密码将明文存储");
            return plainText;
        }
        try {
            SecretKeySpec keySpec = new SecretKeySpec(aesKey.getBytes(StandardCharsets.UTF_8), "AES");
            Cipher cipher = Cipher.getInstance("AES/ECB/PKCS5Padding");
            cipher.init(Cipher.ENCRYPT_MODE, keySpec);
            byte[] encrypted = cipher.doFinal(plainText.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(encrypted);
        } catch (Exception e) {
            throw new RuntimeException("密码加密失败", e);
        }
    }

    public static String decrypt(String cipherText) {
        if (cipherText == null || cipherText.isEmpty()) return cipherText;
        if (aesKey == null || aesKey.isEmpty()) {
            log.warn("[PasswordUtil] AES密钥未配置，返回原始值");
            return cipherText;
        }
        try {
            SecretKeySpec keySpec = new SecretKeySpec(aesKey.getBytes(StandardCharsets.UTF_8), "AES");
            Cipher cipher = Cipher.getInstance("AES/ECB/PKCS5Padding");
            cipher.init(Cipher.DECRYPT_MODE, keySpec);
            byte[] decrypted = cipher.doFinal(Base64.getDecoder().decode(cipherText));
            return new String(decrypted, StandardCharsets.UTF_8);
        } catch (Exception e) {
            log.error("[PasswordUtil] 密码解密失败", e);
            throw new RuntimeException("密码解密失败", e);
        }
    }
}
