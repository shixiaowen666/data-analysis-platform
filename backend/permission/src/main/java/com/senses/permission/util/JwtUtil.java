package com.senses.permission.util;

import io.jsonwebtoken.*;
import io.jsonwebtoken.io.Encoders;
import org.apache.commons.codec.binary.Base64;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.Key;
import java.security.KeyFactory;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import io.jsonwebtoken.security.Keys;

public class JwtUtil {

    /**
     * 根据签名密钥，签名算法，⽤户id⽣成JWT字符串
     *
     * @param signAlgorithm 签名算法，必需，⽀持HS256，RS256
     * @param signingKey 签名密钥，必需
     * 如果签名算法为HS256，signingKey就是⼀串32位以上由数字和字⺟组成的字符串
     * 如果签名算法为RS256，singingKey就是base64编码的RSA私钥数据，注意不能包含空格和换⾏符，
     * 不能包含-----xxx-----格式的前后缀
     * @param userId 要⼀键跳转到的IDaaS⽤户id，必需
     */
    public static String generateJWT(String signingKey, String userId) {
        // 构造header
        Map<String, Object> header = new HashMap<>();
        // alg 算法名，根据用户指定的算法来设置
        header.put("alg", "HS256");
        // typ 类型，固定为JWT
        header.put("typ", "JWT");

        // 构造payload里的参数
        // 以下三个参数需要根据实际情况修改
        // sub 外部用户id，必需，表示要登录到哪个IDaaS用户
        String subject = userId;
        // exp 过期时间，必需，这里推荐为600s
        Date expirationTime = new Date(System.currentTimeMillis() + 86400 * 1000);
        // jti 用于防重放的随机id，必需
        String jwtId = UUID.randomUUID().toString();

        // 以下三个参数值建议不修改
        // iss 发布者id，可选
        String issuer = "JWT Provider";
        // aud 接收方身份标志，可选
        String audience = "Bce IDaaS";
        // iat 颁发时间，可选
        Date issuedAt = new Date();

        // 生成jwt
        JwtBuilder jwtBuilder =
                Jwts.builder()
                        .setHeader(header)
                        .setIssuer(issuer)
                        .setSubject(subject)
                        .setAudience(audience)
                        .setIssuedAt(issuedAt)
                        .setExpiration(expirationTime)
                        .setId(jwtId).signWith(generateSigningKey(signingKey, "HS256"))
                        .base64UrlEncodeWith(Encoders.BASE64URL);


        return jwtBuilder.compact();
    }

    /**
     * 根据私钥数据和签名算法⽣成Key对象
     *
     * @param signingKey pkcs1格式的未加密RSA密钥，不包含前后缀，空格，换⾏符等符号
     * @param signAlgorithm
     * @return
     */
    private static Key generateSigningKey(String signingKey, String signAlgorithm) {
        if (signAlgorithm.equals("RS256")) {
            signingKey = signingKey.replaceAll("\n", "");
            byte[] valueDecoded = Base64.decodeBase64(signingKey);
            PKCS8EncodedKeySpec keySpec = new PKCS8EncodedKeySpec(valueDecoded);
            try {
                KeyFactory rsaFact = KeyFactory.getInstance("RSA");
                return rsaFact.generatePrivate(keySpec);
            } catch (Exception e) {
                throw new RuntimeException("私钥解析异常");
            }
        } else {
            return new SecretKeySpec(signingKey.getBytes(),
                    SignatureAlgorithm.valueOf(signAlgorithm).getJcaName());
        }
    }

    public static String verify(String token, String signingKey) {
        try {
            SecretKey key = Keys.hmacShaKeyFor(signingKey.getBytes(StandardCharsets.UTF_8));
            return Jwts.parser()
                    .verifyWith(key)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload()
                    .getSubject();
        } catch (JwtException e) {
            return null;
        }
    }

    public static void main(String[] args) {
//        String token = JwtUtil.generateJWT("HS256", "2f698118a46e4d6cace235a3f8d2d79a", "435275");
//        String token = "eyJ0eXAiOiJKV1QiLCJhbGciOiJIUzI1NiJ9.eyJpc3MiOiJKV1QgUHJvdmlkZXIiLCJzdWIiOiI0MzUyNzUiLCJhdWQiOiJCY2UgSURhYVMiLCJpYXQiOjE3NTIyMDUyNTMsImV4cCI6MTc1MjIwNTMxMywianRpIjoiMDlhMmNhNzItODcyYy00OGI4LWE0MDctZmJjYmE1NTEzODBkIn0.U0QFMGytLRyQsOvaNneXMA1XlnudHXBzYV50WM21cHQ";
//        System.out.println("生成的token："+ token);
//        String userId = JwtUtil.verify(token,"2f698118a46e4d6cace235a3f8d2d79a");
//        System.out.println("userId:"+ userId);
//        String token = JwtUtil.generateJWT("nvUO7rrpGiWhxDGa8C0EulV65eTYLbBWsivdxkYJpk", "gongyinglian13");
        String token = "eyJ0eXAiOiJKV1QiLCJhbGciOiJIUzUxMiJ9.eyJpc3MiOiJKV1QgUHJvdmlkZXIiLCJzdWIiOiJnb25neWluZ2xpYW4xMyIsImF1ZCI6IkJjZSBJRGFhUyIsImlhdCI6MTc4NTI0OTU2MCwiZXhwIjoxNzg1MzM1OTYwLCJqdGkiOiJiYTVkYTlmMi0yNTA3LTQ1MDEtOWE1Yy1lMjllYmQ1YjliZWMifQ.NHXcOgJfdVrXNLgt9EzBrTBhVKsk1GzbwTHbnGMVNdiEjBLq6Cnvhon9v16cqmx3uKVcJwsuJPm_iHFaW20CSw";
        String user = JwtUtil.verify(token, "4c2cde3f74bb49cc9e21e139604258de4c2cde3f74bb49cc9e21e139604258de");
        System.out.println(user);
    }


}
