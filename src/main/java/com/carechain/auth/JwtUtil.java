package com.carechain.auth;

import jakarta.annotation.PostConstruct;
import io.jsonwebtoken.*;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.util.Date;

@Component
public class JwtUtil {

    @Value("${jwt.secret}")
    private String secret;

    @Value("${jwt.expiration}")
    private long expiration;

    private SecretKey signingKey;

    @PostConstruct
    void validateConfiguration() {
        byte[] keyBytes;
        try {
            keyBytes = Decoders.BASE64.decode(secret);
        } catch (IllegalArgumentException exception) {
            throw new IllegalStateException("JWT secret must be valid Base64", exception);
        }
        if (keyBytes.length < 32) {
            throw new IllegalStateException("JWT secret must decode to at least 32 bytes");
        }
        if (expiration <= 0) {
            throw new IllegalStateException("JWT expiration must be greater than zero");
        }
        signingKey = Keys.hmacShaKeyFor(keyBytes);
    }

    private SecretKey getSigningKey() {
        return signingKey;
    }

    public String generateToken(String email, String role) {
        return Jwts.builder()
                .subject(email)
                .claim("role", role)
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + expiration))
                .signWith(getSigningKey())
                .compact();
    }

    public String extractEmail(String token) {
        return extractClaims(token).getSubject();
    }

    public String extractRole(String token) {
        return extractClaims(token).get("role", String.class);
    }

    public boolean isTokenValid(String token) {
        try {
            extractClaims(token);
            return true;
        } catch (JwtException | IllegalArgumentException e) {
            return false;
        }
    }

    private Claims extractClaims(String token) {
        return Jwts.parser()
                .verifyWith(getSigningKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }
}
