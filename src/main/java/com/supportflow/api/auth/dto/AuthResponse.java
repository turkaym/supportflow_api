package com.supportflow.api.auth.dto;

import com.supportflow.api.user.Role;
import java.util.UUID;

public record AuthResponse(
        UUID id,
        String email,
        Role role,
        String tokenType,
        String accessToken,
        long expiresIn
) {
    public static AuthResponse bearer(UUID id, String email, Role role, String accessToken, long expiresIn) {
        return new AuthResponse(id, email, role, "Bearer", accessToken, expiresIn);
    }
}
