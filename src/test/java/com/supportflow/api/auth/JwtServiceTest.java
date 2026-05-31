package com.supportflow.api.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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
}
