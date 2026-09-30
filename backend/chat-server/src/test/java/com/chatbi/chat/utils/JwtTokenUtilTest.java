package com.chatbi.chat.utils;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.*;

/**
 * LoginFilter 用到的 3 个方法单测
 */
class JwtTokenUtilTest {

    private static final String SECRET = "chatbi-jwt-secret-key-for-production-2026";

    private JwtTokenUtil jwtTokenUtil;

    @BeforeEach
    void setUp() {
        jwtTokenUtil = new JwtTokenUtil();
        ReflectionTestUtils.setField(jwtTokenUtil, "secret", SECRET);
        ReflectionTestUtils.setField(jwtTokenUtil, "expiration", 86400000L);
        ReflectionTestUtils.setField(jwtTokenUtil, "tokenHeader", "Authorization");
    }

    @Test
    void getUsernameAndUserId() {
        String token = jwtTokenUtil.generateToken("testUser", 1001L);

        assertEquals("testUser", jwtTokenUtil.getUsernameFromToken(token));
        assertEquals(1001L, jwtTokenUtil.getUserIdFromToken(token));
    }

    @Test
    void isTokenExpired_false() {
        String token = jwtTokenUtil.generateToken("testUser", 1001L);

        assertFalse(jwtTokenUtil.isTokenExpired(token));
    }

    @Test
    void verifyRealTokenFromPermission() {
        JwtTokenUtil verifier = new JwtTokenUtil();
        ReflectionTestUtils.setField(verifier, "secret", "my-super-secret-jwt-key-for-chatbi-2026");
        ReflectionTestUtils.setField(verifier, "expiration", 86400000L);

        String realToken = "eyJhbGciOiJIUzI1NiJ9.eyJ1c2VyX2lkIjoxLCJzdWIiOiJhZG1pbiIsImlhdCI6MTc4NDEzNDcwMCwiZXhwIjoxNzg0MjIxMTAwfQ.zP_9BCLMDeZDHqk2Ywg5ouRVvfJf2thEYP0MJ6Bvgfw";

        // secret 匹配 → 解析成功（parser 默认不校验过期，校验由 isTokenExpired 单独做）
        assertEquals("admin", verifier.getUsernameFromToken(realToken));
        assertEquals(1L, verifier.getUserIdFromToken(realToken));

        // token 在有效期内
        assertFalse(verifier.isTokenExpired(realToken));
    }

    @Test
    void isTokenExpired_true() {
        ReflectionTestUtils.setField(jwtTokenUtil, "expiration", 1L);
        String token = jwtTokenUtil.generateToken("testUser", 1001L);

        try {
            Thread.sleep(2);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        assertTrue(jwtTokenUtil.isTokenExpired(token));
    }
}
