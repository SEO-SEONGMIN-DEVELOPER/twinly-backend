package com.nidus.twinly.common.jwt;

import com.nidus.twinly.auth.dto.result.AuthTokenResult;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;

@Component
public class JwtService {

    private static final Duration ACCESS_TOKEN_EXPIRES_IN = Duration.ofMinutes(30);
    private static final Duration REFRESH_TOKEN_EXPIRES_IN = Duration.ofDays(14);

    private final SecretKey key;

    public JwtService(JwtProperties properties) {
        this.key = Keys.hmacShaKeyFor(Base64.getDecoder().decode(properties.secretKey()));
    }

    public AuthTokenResult generateAuthTokenResult(Long userId) {
        Jwt accessToken = generateAccessToken(userId);
        Jwt refreshToken = generateAccessToken(userId);

        return new AuthTokenResult(accessToken.value(), accessToken.expiresAt(), refreshToken.value(), refreshToken.expiresAt());
    }

    public Jwt generateAccessToken(Long userId) {
        Instant expiresAt = Instant.now().plus(ACCESS_TOKEN_EXPIRES_IN);
        return new Jwt(generate(userId, JwtType.ACCESS, expiresAt), expiresAt);
    }

    public Jwt generateRefreshToken(Long userId) {
        Instant expiresAt = Instant.now().plus(REFRESH_TOKEN_EXPIRES_IN);
        return new Jwt(generate(userId, JwtType.REFRESH, expiresAt), expiresAt);
    }

    private String generate(Long userId, JwtType type, Instant expiresAt) {
        return Jwts.builder()
                .subject(userId.toString())
                .claim("type", type.name())
                .expiration(Date.from(expiresAt))
                .signWith(key)
                .compact();
    }

    public Long parseAccessTokenUserId(String token) {
        return parseUserId(token, JwtType.ACCESS);
    }

    public Long parseRefreshTokenUserId(String token) {
        return parseUserId(token, JwtType.REFRESH);
    }

    private Long parseUserId(String token, JwtType expectedType) {
        Claims claims = Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .getPayload();

        if (!expectedType.name().equals(claims.get("type", String.class))) {
            throw new JwtException("토큰 타입이 일치하지 않습니다.");
        }

        return Long.valueOf(claims.getSubject());
    }
}
