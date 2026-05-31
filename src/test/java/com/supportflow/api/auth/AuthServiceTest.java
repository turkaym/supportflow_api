package com.supportflow.api.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.supportflow.api.auth.dto.AuthResponse;
import com.supportflow.api.auth.dto.LoginRequest;
import com.supportflow.api.auth.dto.RegisterRequest;
import com.supportflow.api.user.Role;
import com.supportflow.api.user.User;
import com.supportflow.api.user.UserRepository;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.web.server.ResponseStatusException;

class AuthServiceTest {

    private final UserRepository userRepository = mock(UserRepository.class);
    private final JwtService jwtService = mock(JwtService.class);
    private final AuthenticationManager authenticationManager = mock(AuthenticationManager.class);
    @SuppressWarnings("unchecked")
    private final ObjectProvider<AuthenticationManager> authenticationManagerProvider = mock(ObjectProvider.class);
    private final AuthService authService = new AuthService(userRepository, jwtService, authenticationManagerProvider);

    @Test
    void registerNormalizesEmailHashesPasswordAndReturnsSafeResponse() {
        when(userRepository.existsByEmail("person@example.com")).thenReturn(false);
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(jwtService.generateToken("person@example.com")).thenReturn("jwt-token");
        when(jwtService.getExpirationMillis()).thenReturn(3_600_000L);

        AuthResponse response = authService.register(new RegisterRequest(" Person@Example.COM ", "Secret123!"));

        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(userCaptor.capture());
        User savedUser = userCaptor.getValue();

        assertThat(savedUser.getEmail()).isEqualTo("person@example.com");
        assertThat(savedUser.getRole()).isEqualTo(Role.USER);
        assertThat(savedUser.getPasswordHash()).isNotEqualTo("Secret123!");
        assertThat(savedUser.getPasswordHash()).startsWith("$2");
        assertThat(response.email()).isEqualTo("person@example.com");
        assertThat(response.accessToken()).isEqualTo("jwt-token");
        assertThat(response.tokenType()).isEqualTo("Bearer");
    }

    @Test
    void registerRejectsDuplicateEmailWithConflict() {
        when(userRepository.existsByEmail("person@example.com")).thenReturn(true);

        assertThatThrownBy(() -> authService.register(new RegisterRequest("person@example.com", "Secret123!")))
                .isInstanceOf(ResponseStatusException.class)
                .extracting("statusCode")
                .isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    void loginAuthenticatesAndReturnsToken() {
        User user = new User("person@example.com", "$2a$10$hash", Role.USER);
        when(authenticationManagerProvider.getIfAvailable()).thenReturn(authenticationManager);
        when(userRepository.findByEmail("person@example.com")).thenReturn(Optional.of(user));
        when(jwtService.generateToken("person@example.com")).thenReturn("jwt-token");
        when(jwtService.getExpirationMillis()).thenReturn(3_600_000L);

        AuthResponse response = authService.login(new LoginRequest(" Person@Example.COM ", "Secret123!"));

        verify(authenticationManager).authenticate(new UsernamePasswordAuthenticationToken("person@example.com", "Secret123!"));
        assertThat(response.accessToken()).isEqualTo("jwt-token");
        assertThat(response.email()).isEqualTo("person@example.com");
    }

    @Test
    void loginRejectsInvalidCredentialsWithGenericUnauthorized() {
        when(authenticationManagerProvider.getIfAvailable()).thenReturn(authenticationManager);
        when(authenticationManager.authenticate(any())).thenThrow(new BadCredentialsException("bad"));

        assertThatThrownBy(() -> authService.login(new LoginRequest("person@example.com", "wrong-password")))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(error -> {
                    ResponseStatusException exception = (ResponseStatusException) error;
                    assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
                    assertThat(exception.getReason()).isEqualTo("Invalid email or password");
                });
    }
}
