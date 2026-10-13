package com.zhishuiyun.water.security;

import com.zhishuiyun.water.entity.DomainEnums;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.nio.file.FileAlreadyExistsException;
import java.io.IOException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Date;

/**
 * JWT 生成与解析（jjwt 0.12 API）。
 */
@Service
public class JwtService {

    private final SecretKey key;
    private final long expireMillis;

    public JwtService(@Value("${jwt.secret:}") String secret,
                      @Value("${jwt.expire-minutes}") long expireMinutes,
                      @Value("${jwt.secret-file:./.jwt-secret}") String secretFile) {
        String signingSecret = secret == null || secret.isBlank() ? loadOrCreateSecret(secretFile) : secret;
        if (signingSecret.getBytes(StandardCharsets.UTF_8).length < 32) {
            throw new IllegalArgumentException("JWT signing secret must contain at least 32 bytes");
        }
        this.key = Keys.hmacShaKeyFor(signingSecret.getBytes(StandardCharsets.UTF_8));
        this.expireMillis = expireMinutes * 60_000L;
    }

    private static String loadOrCreateSecret(String secretFile) {
        Path path = Path.of(secretFile).toAbsolutePath().normalize();
        try {
            if (!Files.exists(path)) {
                Files.createDirectories(path.getParent());
                byte[] random = new byte[64];
                new SecureRandom().nextBytes(random);
                try {
                    Files.writeString(path, Base64.getEncoder().encodeToString(random),
                            StandardCharsets.UTF_8, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
                } catch (FileAlreadyExistsException ignored) {
                    // Another instance initialized the same persisted secret first.
                }
            }
            return Files.readString(path, StandardCharsets.UTF_8).trim();
        } catch (IOException e) {
            throw new IllegalStateException("Cannot initialize local JWT signing key; configure JWT_SECRET", e);
        }
    }

    public String generate(String username, DomainEnums.AccountRole role, String name) {
        Date now = new Date();
        return Jwts.builder()
                .subject(username)
                .claim("role", role.name())
                .claim("name", name)
                .issuedAt(now)
                .expiration(new Date(now.getTime() + expireMillis))
                .signWith(key)
                .compact();
    }

    /**
     * 解析 token，失败抛出 JwtException。
     */
    public Claims parse(String token) {
        return Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }
}
