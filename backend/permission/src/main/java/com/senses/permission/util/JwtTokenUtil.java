package com.senses.permission.util;

import com.senses.permission.entity.User;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.time.DateUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.io.Serializable;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

@Component
@Slf4j
public class JwtTokenUtil implements Serializable {

    private static final long serialVersionUID = -3301605591108950415L;

    @Value("${jwt.secret}")
    private String secret;

    @Value("${jwt.expiration}")
    private Long expiration;

    @Value("${jwt.header}")
    private String tokenHeader;

    private SecretKey getSigningKey() {
        return Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }

    public String getUsernameFromToken(String token) {
        return getClaimFromToken(token, Claims::getSubject);
    }
    public Long getUserIdFromToken(String token){
        return getAllClaimsFromToken(token).get("user_id",Long.class);
    }
    public Long getTenantIdFromToken(String token){
        return getAllClaimsFromToken(token).get("tenant_id",Long.class);
    }

    public Date getIssuedAtDateFromToken(String token) {
        return getClaimFromToken(token, Claims::getIssuedAt);
    }

    public Date getExpirationDateFromToken(String token) {
        return getClaimFromToken(token, Claims::getExpiration);
    }

    public <T> T getClaimFromToken(String token, Function<Claims, T> claimsResolver) {
        final Claims claims = getAllClaimsFromToken(token);
        return claimsResolver.apply(claims);
    }

    private Claims getAllClaimsFromToken(String token) {
        return Jwts.parser()
                .verifyWith(getSigningKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    public String verify(String token, String signingKey) {
        try {
            SecretKey key = Keys.hmacShaKeyFor(signingKey.getBytes(StandardCharsets.UTF_8));
            return Jwts.parser()
                    .verifyWith(key)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload()
                    .getSubject();
        } catch (JwtException e) {
            log.info("token verify failed: {}", e.getMessage());
            return null;
        }
    }

    public Boolean isTokenExpired(String token) {
        Date expiration;
        try{
            expiration = getExpirationDateFromToken(token);
            log.info("expiration == ");
        }catch (Exception e){
            log.info("isTokenExpired method Exception===", e);
            return true;
        }
        return expiration.before(new Date());
    }

    private Boolean isCreatedBeforeLastPasswordReset(Date created, Date lastPasswordReset) {
        return (lastPasswordReset != null && created.before(lastPasswordReset));
    }

    private Boolean ignoreTokenExpiration(String token) {
        // here you specify tokens, for that the expiration is ignored
        return false;
    }

    public String generateToken(String username,Long uid) {
        return doGenerateToken(buildClaims(uid), username);
    }

    public String generateToken(String username,Long uid,Date expirationDate) {
        return doGenerateToken(buildClaims(uid), username, expirationDate);
    }

    private Map<String, Object> buildClaims(Long uid) {
        Map<String, Object> claims = new HashMap<>();
        claims.put("user_id",uid);
        claims.put("tenant_id",1L);
        return claims;
    }

    private String doGenerateToken(Map<String, Object> claims, String subject) {
        final Date createdDate = new Date();
        return doGenerateToken(claims, subject, calculateExpirationDate(createdDate));
    }

    private String doGenerateToken(Map<String, Object> claims, String subject, Date expirationDate) {
        final Date createdDate = new Date();
        return Jwts.builder()
                .claims(claims)
                .subject(subject)
                .issuedAt(createdDate)
                .expiration(expirationDate)
                .signWith(getSigningKey())
                .compact();
    }

    public Boolean canTokenBeRefreshed(String token, Date lastPasswordReset) {
        final Date created = getIssuedAtDateFromToken(token);
        return !isCreatedBeforeLastPasswordReset(created, lastPasswordReset)
                && (!isTokenExpired(token) || ignoreTokenExpiration(token));
    }

    public String refreshToken(String token) {
        final Date createdDate = new Date();
        final Date expirationDate = calculateExpirationDate(createdDate);

        final Claims claims = getAllClaimsFromToken(token);
        return Jwts.builder()
                .claims(claims)
                .issuedAt(createdDate)
                .expiration(expirationDate)
                .signWith(getSigningKey())
                .compact();
    }

    public Boolean validateToken(String token, User user) {
        final Date created = getIssuedAtDateFromToken(token);
        return (!isTokenExpired(token)
                && !isCreatedBeforeLastPasswordReset(created, user.getLastPasswordResetTime())
        );
    }

    private Date calculateExpirationDate(Date createdDate) {
        return new Date(createdDate.getTime() + expiration);
    }

    public static void main(String[] args) {
        String signingKey = "4c2cde3f74bb49cc9e21e139604258de4c2cde3f74bb49cc9e21e139604258de";
        String token = "eyJ0eXAiOiJKV1QiLCJhbGciOiJIUzUxMiJ9.eyJpc3MiOiJKV1QgUHJvdmlkZXIiLCJzdWIiOiJnb25neWluZ2xpYW4xMyIsImF1ZCI6IkJjZSBJRGFhUyIsImlhdCI6MTc4NTI0OTU2MCwiZXhwIjoxNzg1MzM1OTYwLCJqdGkiOiJiYTVkYTlmMi0yNTA3LTQ1MDEtOWE1Yy1lMjllYmQ1YjliZWMifQ.NHXcOgJfdVrXNLgt9EzBrTBhVKsk1GzbwTHbnGMVNdiEjBLq6Cnvhon9v16cqmx3uKVcJwsuJPm_iHFaW20CSw";

//        // 用外部key生成token
//        SecretKey key = Keys.hmacShaKeyFor(signingKey.getBytes(StandardCharsets.UTF_8));
//        String token = Jwts.builder()
//                .subject(username)
//                .issuedAt(new Date())
//                .expiration(new Date(System.currentTimeMillis() + 86400 * 1000))
//                .signWith(key)
//                .compact();
//
//        System.out.println("生成的token: " + token);

        // 正确key解签
        JwtTokenUtil util = new JwtTokenUtil();
        String result = util.verify(token, signingKey);
        System.out.println("正确key解签结果: " + result); // 预期: testUser

//        // 错误key解签
//        String wrongResult = util.verify(token, "wrongKey_wrongKey_wrongKey_wrongKey_32byte");
//        System.out.println("错误key解签结果: " + wrongResult); // 预期: null
    }
}
