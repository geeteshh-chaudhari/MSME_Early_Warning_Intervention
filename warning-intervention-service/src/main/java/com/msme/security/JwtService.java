package com.msme.security;

import java.time.Instant;
import java.util.Date;

import javax.crypto.SecretKey;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;

@Service
public class JwtService {

    private final SecretKey signingKey;
    private final long expirationMs;
    private final String issuer;

    public JwtService(
            @Value("${app.jwt.secret}") String secret,
            @Value("${app.jwt.expiration-ms}") long expirationMs,
            @Value("${app.jwt.issuer}") String issuer) {

        byte[] keyBytes = Decoders.BASE64.decode(secret);

        if (keyBytes.length < 32) {
            throw new IllegalArgumentException(
                "JWT secret must contain at least 32 bytes"
            );
        }

        if (expirationMs <= 0) {
            throw new IllegalArgumentException(
                "JWT expiration must be positive"
            );
        }

        if (issuer == null || issuer.isBlank()) {
            throw new IllegalArgumentException(
                "JWT issuer must not be blank"
            );
        }

        this.signingKey = Keys.hmacShaKeyFor(keyBytes);
        this.expirationMs = expirationMs;
        this.issuer = issuer;
    }

    public String generateToken(CustomUserPrincipal user) {

        Instant now = Instant.now();

        return Jwts.builder()
                .subject(user.getUserId().toString())
                .claim("role", user.getRole())
                .issuer(issuer)
                .issuedAt(Date.from(now))
                .expiration(
                    Date.from(now.plusMillis(expirationMs))
                )
                .signWith(signingKey, Jwts.SIG.HS256)
                .compact();
    }

    private Claims extractAllClaims(String token) {

        return Jwts.parser()
                .verifyWith(signingKey)
                .requireIssuer(issuer)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    public Long extractUserId(String token) {

        String subject = extractAllClaims(token)
                .getSubject();

        return Long.valueOf(subject);
    }

    public String extractRole(String token) {

        return extractAllClaims(token)
                .get("role", String.class);
    }

    public boolean isTokenValid(
            String token,
            CustomUserPrincipal user) {

        try {

            Claims claims = extractAllClaims(token);

            String subject = claims.getSubject();
            String role = claims.get("role", String.class);

            return subject != null
                    && subject.equals(user.getUserId().toString())
                    && role != null
                    && role.equals(user.getRole());

        } catch (RuntimeException ex) {

            return false;
        }
    }
}