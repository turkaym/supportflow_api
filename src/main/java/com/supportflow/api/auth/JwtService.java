package com.supportflow.api.auth;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.util.Date;
import javax.crypto.SecretKey;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class JwtService {

    private static final int MINIMUM_HMAC_SECRET_BYTES = 32;

    private final SecretKey signingKey;
    private final long expirationMillis;
    private final Clock clock;

    @Autowired
    public JwtService(
            @Value("${app.security.jwt.secret}") String secret,
            @Value("${app.security.jwt.expiration}") long expirationMillis
    ) {
        this(secret, expirationMillis, Clock.systemUTC());
    }

    JwtService(String secret, long expirationMillis, Clock clock) {
        if (!StringUtils.hasText(secret)) {
            throw new IllegalStateException("JWT secret must be configured");
        }
        byte[] secretBytes = secret.getBytes(StandardCharsets.UTF_8);
        if (secretBytes.length < MINIMUM_HMAC_SECRET_BYTES) {
            throw new IllegalStateException("JWT secret must be at least 32 bytes for HMAC-SHA signing");
        }
        if (expirationMillis <= 0) {
            throw new IllegalStateException("JWT expiration must be greater than zero");
        }

        this.signingKey = Keys.hmacShaKeyFor(secretBytes);
        this.expirationMillis = expirationMillis;
        this.clock = clock;
    }

    public String generateToken(String subject) {
        Instant issuedAt = clock.instant();
        Instant expiresAt = issuedAt.plusMillis(expirationMillis);

        return Jwts.builder()
                .subject(subject)
                .issuedAt(Date.from(issuedAt))
                .expiration(Date.from(expiresAt))
                .signWith(signingKey)
                .compact();
    }

    public String extractUsername(String token) {
        return extractAllClaims(token).getSubject();
    }

    public boolean isTokenValid(String token) {
        try {
            extractAllClaims(token);
            return true;
        } catch (JwtException | IllegalArgumentException ex) {
            return false;
        }
    }

    public boolean isTokenValid(String token, UserDetails userDetails) {
        try {
            String username = extractUsername(token);
            return username.equals(userDetails.getUsername()) && !isTokenExpired(token);
        } catch (JwtException | IllegalArgumentException ex) {
            return false;
        }
    }

    public long getExpirationMillis() {
        return expirationMillis;
    }

    private boolean isTokenExpired(String token) {
        return extractAllClaims(token).getExpiration().before(Date.from(clock.instant()));
    }

    private Claims extractAllClaims(String token) {
        return Jwts.parser()
                .verifyWith(signingKey)
                .clock(() -> Date.from(clock.instant()))
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }
}
