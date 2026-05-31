package com.supportflow.api.auth;

import com.supportflow.api.auth.dto.AuthResponse;
import com.supportflow.api.auth.dto.LoginRequest;
import com.supportflow.api.auth.dto.RegisterRequest;
import com.supportflow.api.user.Role;
import com.supportflow.api.user.User;
import com.supportflow.api.user.UserRepository;
import java.util.Locale;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class AuthService {

    private static final String INVALID_CREDENTIALS_MESSAGE = "Invalid email or password";

    private final UserRepository userRepository;
    private final JwtService jwtService;
    private final ObjectProvider<AuthenticationManager> authenticationManagerProvider;
    private final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    public AuthService(
            UserRepository userRepository,
            JwtService jwtService,
            ObjectProvider<AuthenticationManager> authenticationManagerProvider
    ) {
        this.userRepository = userRepository;
        this.jwtService = jwtService;
        this.authenticationManagerProvider = authenticationManagerProvider;
    }

    @Transactional
    public AuthResponse register(RegisterRequest request) {
        String email = normalizeEmail(request.email());
        if (userRepository.existsByEmail(email)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Email is already registered");
        }

        String passwordHash = passwordEncoder.encode(request.password());
        User user = userRepository.save(new User(email, passwordHash, Role.USER));
        return createAuthResponse(user);
    }

    @Transactional(readOnly = true)
    public AuthResponse login(LoginRequest request) {
        String email = normalizeEmail(request.email());
        AuthenticationManager authenticationManager = authenticationManagerProvider.getIfAvailable();
        if (authenticationManager == null) {
            throw unauthorized();
        }

        try {
            authenticationManager.authenticate(new UsernamePasswordAuthenticationToken(email, request.password()));
        } catch (AuthenticationException ex) {
            throw unauthorized();
        }

        User user = userRepository.findByEmail(email)
                .orElseThrow(AuthService::unauthorized);
        return createAuthResponse(user);
    }

    private AuthResponse createAuthResponse(User user) {
        String token = jwtService.generateToken(user.getEmail());
        return AuthResponse.bearer(user.getId(), user.getEmail(), user.getRole(), token, jwtService.getExpirationMillis());
    }

    private static String normalizeEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }

    private static ResponseStatusException unauthorized() {
        return new ResponseStatusException(HttpStatus.UNAUTHORIZED, INVALID_CREDENTIALS_MESSAGE);
    }
}
