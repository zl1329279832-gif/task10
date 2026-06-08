package com.campus.trade.common.util;
import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
@Slf4j @Component
public class JwtUtil {
    private final SecretKey key; private final long expiration; private final long refreshExpiration;
    public JwtUtil(@Value("${jwt.secret}") String secret, @Value("${jwt.expiration}") long exp, @Value("${jwt.refresh-expiration}") long rexp) {
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8)); this.expiration = exp; this.refreshExpiration = rexp;
    }
    public String generateToken(Long userId, String username, String roles) {
        return Jwts.builder().subject(String.valueOf(userId)).claim("username",username).claim("roles",roles)
            .issuedAt(new Date()).expiration(new Date(System.currentTimeMillis()+expiration)).signWith(key).compact();
    }
    public String generateRefreshToken(Long userId) {
        return Jwts.builder().subject(String.valueOf(userId)).claim("type","refresh")
            .issuedAt(new Date()).expiration(new Date(System.currentTimeMillis()+refreshExpiration)).signWith(key).compact();
    }
    public Claims parseToken(String token) { return Jwts.parser().verifyWith(key).build().parseSignedClaims(token).getPayload(); }
    public Long getUserId(String token) { return Long.parseLong(parseToken(token).getSubject()); }
    public String getUsername(String token) { return parseToken(token).get("username",String.class); }
    public String getRoles(String token) { return parseToken(token).get("roles",String.class); }
    public boolean validateToken(String token) { try { parseToken(token); return true; } catch (Exception e) { return false; } }
}
