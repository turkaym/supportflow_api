package com.supportflow.api.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;

class JwtServiceTest {

    private static final String SECRET = "test-secret-for-jwt-service-1234567890";

    @Test
    void generatesAndValidatesToken() {
        JwtService jwtService = new JwtService(SECRET, 3_600_000);

        String token = jwtService.generateToken("person@example.com");

        assertThat(jwtService.extractUsername(token)).isEqualTo("person@example.com");
        assertThat(jwtService.isTokenValid(token)).isTrue();
    }

    @Test
    void rejectsWeakSecretAtStartup() {
        assertThatThrownBy(() -> new JwtService("short-secret", 3_600_000))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("at least 32 bytes");
    }

    @Test
    void rejectsMalformedTokenSafely() {
        JwtService jwtService = new JwtService(SECRET, 3_600_000);

        assertThat(jwtService.isTokenValid("not-a-jwt")).isFalse();
    }

    @Test
    void rejectsExpiredToken() {
        Instant issuedAt = Instant.parse("2026-01-01T00:00:00Z");
        JwtService issuer = new JwtService(SECRET, 1_000, Clock.fixed(issuedAt, ZoneOffset.UTC));
        String token = issuer.generateToken("person@example.com");
        JwtService validator = new JwtService(
                SECRET,
                1_000,
                Clock.fixed(issuedAt.plusMillis(1_001), ZoneOffset.UTC)
        );

        assertThat(validator.isTokenValid(token)).isFalse();
    }

    @Test
    void rejectsTokenSignedWithDifferentSecret() {
        JwtService issuer = new JwtService("different-test-secret-for-jwt-1234567890", 3_600_000);
        JwtService validator = new JwtService(SECRET, 3_600_000);
        String token = issuer.generateToken("person@example.com");

        assertThat(validator.isTokenValid(token)).isFalse();
    }
}
