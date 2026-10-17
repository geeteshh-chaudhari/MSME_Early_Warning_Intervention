package com.msme.security;

import java.util.Set;

import javax.crypto.SecretKey;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;

@Service
public class JwtService {

    private static final Set<String> VALID_ROLES =
            Set.of("RISK_ADMIN", "RM", "MSME_REP");

    private final SecretKey signingKey;
    private final String issuer;

    public JwtService(
            @Value("${app.jwt.secret}") String secret,
            @Value("${app.jwt.issuer}") String issuer) {

        byte[] keyBytes = Decoders.BASE64.decode(secret);

        if (keyBytes.length < 32) {
            throw new IllegalArgumentException(
                    "JWT secret must contain at least 32 bytes"
            );
        }

        if (issuer == null || issuer.isBlank()) {
            throw new IllegalArgumentException(
                    "JWT issuer must not be blank"
            );
        }

        this.signingKey = Keys.hmacShaKeyFor(keyBytes);
        this.issuer = issuer;
    }

    public Claims validateAndExtractClaims(String token) {

        var parsed = Jwts.parser()
                .verifyWith(signingKey)
                .requireIssuer(issuer)
                .build()
                .parseSignedClaims(token);

        if (!"HS256".equals(parsed.getHeader().getAlgorithm())) {
            throw new JwtException("Unsupported JWT algorithm");
        }

        Claims claims = parsed.getPayload();

        if (claims.getExpiration() == null) {
            throw new JwtException("JWT expiration is required");
        }

        String subject = claims.getSubject();

        if (subject == null || Long.parseLong(subject) <= 0) {
            throw new JwtException("Invalid JWT subject");
        }

        String role = claims.get("role", String.class);

        if (role == null || !VALID_ROLES.contains(role)) {
            throw new JwtException("Invalid JWT role");
        }

        return claims;
    }
}