package com.financetracker.controller;

import com.financetracker.dto.UserDTO;
import com.financetracker.model.RefreshToken;
import com.financetracker.model.User;
import com.financetracker.repository.UserRepository;
import com.financetracker.security.JwtTokenProvider;
import com.financetracker.service.RefreshTokenService;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@RestController
@RequestMapping("/api/auth")
@Slf4j
public class AuthController {
    
    @Autowired
    private UserRepository userRepository;
    
    @Autowired
    private PasswordEncoder passwordEncoder;
    
    @Autowired
    private JwtTokenProvider tokenProvider;

    @Autowired
    private RefreshTokenService refreshTokenService;
    
    @PostMapping("/register")
    public ResponseEntity<AuthResponse> register(@Valid @RequestBody RegisterRequest request) {
        if (userRepository.findByEmail(request.getEmail()).isPresent()) {
            return ResponseEntity.badRequest().body(
                new AuthResponse(null, null, null, "Registration failed. Please try again.")
            );
        }
        
        User user = User.builder()
                .username(request.getUsername())
                .email(request.getEmail())
                .passwordHash(passwordEncoder.encode(request.getPassword()))
                .build();
        
        User savedUser = userRepository.save(user);
        String token = tokenProvider.generateToken(savedUser.getId(), savedUser.getEmail());
        RefreshToken refreshToken = refreshTokenService.createRefreshToken(savedUser.getId());
        
        log.info("User registered successfully: userId={}", savedUser.getId());
        return ResponseEntity.ok(new AuthResponse(
                token,
                refreshToken.getToken().toString(),
                savedUser.getId().toString(),
                "Registration successful"));
    }
    
    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest request) {
        Optional<User> userOpt = userRepository.findByEmail(request.getEmail());
        
        if (userOpt.isEmpty()) {
            return ResponseEntity.badRequest().body(
                new AuthResponse(null, null, null, "Invalid email or password")
            );
        }
        
        User user = userOpt.get();
        if (!passwordEncoder.matches(request.getPassword(), user.getPasswordHash())) {
            return ResponseEntity.badRequest().body(
                new AuthResponse(null, null, null, "Invalid email or password")
            );
        }
        
        String token = tokenProvider.generateToken(user.getId(), user.getEmail());
        RefreshToken refreshToken = refreshTokenService.createRefreshToken(user.getId());
        
        log.info("User logged in successfully: userId={}", user.getId());
        return ResponseEntity.ok(new AuthResponse(
                token,
                refreshToken.getToken().toString(),
                user.getId().toString(),
                "Login successful"));
    }

    /**
     * Exchange a valid refresh token for a new access token.
     * The old refresh token is revoked and a new one is issued (rotation).
     */
    @PostMapping("/refresh")
    public ResponseEntity<?> refresh(@Valid @RequestBody RefreshRequest request) {
        try {
            UUID tokenValue = UUID.fromString(request.getRefreshToken());
            RefreshToken existing = refreshTokenService.validateRefreshToken(tokenValue);

            // Rotate: revoke old, issue new
            refreshTokenService.revokeRefreshToken(tokenValue);
            RefreshToken newRefreshToken = refreshTokenService.createRefreshToken(existing.getUserId());

            User user = userRepository.findById(existing.getUserId())
                    .orElseThrow(() -> new RuntimeException("User not found"));

            String newAccessToken = tokenProvider.generateToken(user.getId(), user.getEmail());

            return ResponseEntity.ok(new AuthResponse(
                    newAccessToken,
                    newRefreshToken.getToken().toString(),
                    user.getId().toString(),
                    "Token refreshed"));
        } catch (Exception e) {
            log.warn("Token refresh failed: {}", e.getMessage());
            return ResponseEntity.status(401).body(Map.of("message", e.getMessage()));
        }
    }

    /**
     * Revoke the supplied refresh token (logout).
     */
    @PostMapping("/logout")
    public ResponseEntity<?> logout(@Valid @RequestBody RefreshRequest request) {
        try {
            UUID tokenValue = UUID.fromString(request.getRefreshToken());
            refreshTokenService.revokeRefreshToken(tokenValue);
        } catch (Exception e) {
            log.warn("Logout: could not revoke token: {}", e.getMessage());
        }
        return ResponseEntity.ok(Map.of("message", "Logged out"));
    }
    
    @Data
    @AllArgsConstructor
    static class RegisterRequest {
        @NotBlank(message = "Username is required")
        @Size(min = 3, max = 50, message = "Username must be between 3 and 50 characters")
        private String username;
        
        @NotBlank(message = "Email is required")
        @Email(message = "Email must be valid")
        private String email;
        
        @NotBlank(message = "Password is required")
        @Size(min = 8, max = 100, message = "Password must be between 8 and 100 characters")
        private String password;
    }
    
    @Data
    @AllArgsConstructor
    static class LoginRequest {
        @NotBlank(message = "Email is required")
        @Email(message = "Email must be valid")
        private String email;
        
        @NotBlank(message = "Password is required")
        private String password;
    }

    @Data
    @AllArgsConstructor
    static class RefreshRequest {
        @NotBlank(message = "refreshToken is required")
        private String refreshToken;
    }
    
    @Data
    @AllArgsConstructor
    static class AuthResponse {
        private String token;
        private String refreshToken;
        private String userId;
        private String message;
    }
}
