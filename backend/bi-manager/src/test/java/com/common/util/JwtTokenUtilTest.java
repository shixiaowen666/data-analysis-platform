package com.common.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.Date;

import static org.junit.jupiter.api.Assertions.*;

/**
 * JwtTokenUtil jjwt 0.12.x 升级验证 —— 跨模块 Token 互通
 */
class JwtTokenUtilTest {

    private static final String UNIFIED_SECRET = "chatbi-jwt-secret-key-for-production-2026";

    // permission 模块真实 Token（alg=HS256）
    private static final String PERMISSION_TOKEN =
            "eyJhbGciOiJIUzI1NiJ9.eyJ1c2VyX2lkIjoxLCJzdWIiOiJhZG1pbiIsImlhdCI6MTc4NDE3NzU2OSwiZXhwIjoxNzg0MjYzOTY5fQ.h-8simZ9LWAg_IrrEejWOw_2YIDkHPH2JNqFwDs8BeE";

    @Test
    @DisplayName("permission 模块真实 Token 解析")
    void shouldParsePermissionToken() {
        // permission 生产密钥 vs bi-manager 统一密钥
        String[][] candidates = {
                {"my-super-secret-jwt-key-for-chatbi-2026", "permission生产密钥"},
                {UNIFIED_SECRET, "bi-manager统一密钥"},
        };

        for (String[] c : candidates) {
            try {
                JwtTokenUtil util = newJwtTokenUtil(c[0]);
                String username = util.getUsernameFromToken(PERMISSION_TOKEN);
                Long userId = util.getUserIdFromToken(PERMISSION_TOKEN);
                Date exp = util.getExpirationDateFromToken(PERMISSION_TOKEN);
                boolean expired = util.isTokenExpired(PERMISSION_TOKEN);

                System.out.println("=== MATCH: " + c[1] + " ===");
                System.out.println("  secret   : " + c[0]);
                System.out.println("  username : " + username);
                System.out.println("  userId   : " + userId);
                System.out.println("  exp      : " + exp);
                System.out.println("  expired  : " + expired);

                assertEquals("admin", username);
                assertEquals(1L, userId);
                return;
            } catch (Exception e) {
                System.out.println("NO: " + c[1] + " -> " + e.getClass().getSimpleName());
            }
        }
        fail("permission Token 无法用已知密钥验证，需确认密钥是否一致");
    }

    @Test
    @DisplayName("签发-解析-校验完整闭环")
    void shouldGenerateParseAndValidateToken() throws Exception {
        JwtTokenUtil util = newJwtTokenUtil(UNIFIED_SECRET);

        String token = util.generateToken("admin", 1L);
        assertNotNull(token);
        assertEquals("admin", util.getUsernameFromToken(token));
        assertEquals(1L, util.getUserIdFromToken(token));
        assertFalse(util.isTokenExpired(token));

        System.out.println("=== 签发 Token 闭环验证通过 ===");
        System.out.println("  token    : " + token);
        System.out.println("  username : " + util.getUsernameFromToken(token));
    }

    @Test
    @DisplayName("refreshToken — Claims 不可变 fix 验证")
    void shouldRefreshTokenWithoutImmutableClaimsError() throws Exception {
        JwtTokenUtil util = newJwtTokenUtil(UNIFIED_SECRET);

        String token = util.generateToken("admin", 1L);
        String refreshed = util.refreshToken(token);

        assertNotNull(refreshed);
        assertNotEquals(token, refreshed);
        assertEquals("admin", util.getUsernameFromToken(refreshed));

        System.out.println("=== refreshToken Claims 不可变 fix 生效 ===");
    }

    private JwtTokenUtil newJwtTokenUtil(String secret) throws Exception {
        JwtTokenUtil util = new JwtTokenUtil();
        setField(util, "secret", secret);
        setField(util, "expiration", 86400000L);
        setField(util, "tokenHeader", "Authorization");
        return util;
    }

    private void setField(Object target, String fieldName, Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(target, value);
    }
}
