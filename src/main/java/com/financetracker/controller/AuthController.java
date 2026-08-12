package com.financetracker.controller;

import com.financetracker.config.RateLimitProperties;
import com.financetracker.model.RefreshToken;
import com.financetracker.model.User;
import com.financetracker.repository.UserRepository;
import com.financetracker.security.JwtTokenProvider;
import com.financetracker.service.RefreshTokenService;
import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

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

    @Autowired
    private RateLimitProperties rateLimitProps;

    // Per-account (email) rate-limit buckets for auth endpoints.
    // Key: normalised email address.  Value: Bucket.
    private final ConcurrentHashMap<String, Bucket> accountBuckets = new ConcurrentHashMap<>();

    // Consecutive-failure counter per email — drives exponential-backoff penalty.
    private final ConcurrentHashMap<String, Integer> failCounts = new ConcurrentHashMap<>();

    // -------------------------------------------------------------------------
    // Endpoints
    // -------------------------------------------------------------------------

    @PostMapping("/register")
    public ResponseEntity<AuthResponse> register(
            @Valid @RequestBody RegisterRequest request,
            HttpServletRequest httpRequest) {

        String normalisedEmail = request.getEmail().toLowerCase();
        if (!consumeAccountBucket(normalisedEmail)) {
            log.warn("Account rate limit hit during register: email={}", normalisedEmail);
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                    .body(new AuthResponse(null, null, null,
                            "Too many registration attempts. Please try again later."));
        }

        if (userRepository.findByEmail(normalisedEmail).isPresent()) {
            // Do NOT reveal whether the email exists — return a generic message.
            return ResponseEntity.badRequest()
                    .body(new AuthResponse(null, null, null, "Registration failed. Please try again."));
        }

        User user = User.builder()
                .username(request.getUsername())
                .email(normalisedEmail)
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
    public ResponseEntity<AuthResponse> login(
            @Valid @RequestBody LoginRequest request,
            HttpServletRequest httpRequest) {

        String normalisedEmail = request.getEmail().toLowerCase();
        if (!consumeAccountBucket(normalisedEmail)) {
            log.warn("Account rate limit hit during login: email={}", normalisedEmail);
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                    .body(new AuthResponse(null, null, null,
                            "Too many login attempts. Please try again later."));
        }

        Optional<User> userOpt = userRepository.findByEmail(normalisedEmail);
        if (userOpt.isEmpty() || !passwordEncoder.matches(request.getPassword(), userOpt.get().getPasswordHash())) {
            // Record failure and apply exponential-backoff penalty to the bucket.
            applyFailurePenalty(normalisedEmail);
            // Return a single generic message — never reveal which field is wrong.
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(new AuthResponse(null, null, null, "Invalid email or password."));
        }

        // Success — reset failure count
        failCounts.remove(normalisedEmail);

        User user = userOpt.get();
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

            refreshTokenService.revokeRefreshToken(tokenValue);
            RefreshToken newRefreshToken = refreshTokenService.createRefreshToken(existing.getUserId());

            User user = userRepository.findById(existing.getUserId())
                    .orElseThrow(() -> new IllegalStateException("User not found for refresh token"));

            String newAccessToken = tokenProvider.generateToken(user.getId(), user.getEmail());

            return ResponseEntity.ok(new AuthResponse(
                    newAccessToken,
                    newRefreshToken.getToken().toString(),
                    user.getId().toString(),
                    "Token refreshed"));
        } catch (Exception e) {
            // Log full detail server-side; surface a generic message to the client.
            log.warn("Token refresh failed: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("message", "Token refresh failed. Please log in again."));
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

    // -------------------------------------------------------------------------
    // Per-account rate limiting helpers
    // -------------------------------------------------------------------------

    /**
     * Consume one token from the per-account (email) bucket.
     * Returns {@code false} when the limit is exceeded (request should be rejected).
     */
    private boolean consumeAccountBucket(String email) {
        Bucket bucket = accountBuckets.computeIfAbsent(email, k -> buildAccountBucket());
        return bucket.tryConsume(1);
    }

    private Bucket buildAccountBucket() {
        Bandwidth limit = Bandwidth.builder()
                .capacity(rateLimitProps.getAuthAccountLimit())
                .refillGreedy(rateLimitProps.getAuthAccountLimit(),
                        Duration.ofSeconds(rateLimitProps.getAuthAccountWindowSeconds()))
                .build();
        return Bucket.builder().addLimit(limit).build();
    }

    /**
     * Exponential-backoff: each consecutive failure drains extra tokens from the
     * account bucket so the next attempt must wait progressively longer.
     * Penalty doubles per failure up to a cap of 16 extra tokens.
     */
    private void applyFailurePenalty(String email) {
        int count = failCounts.merge(email, 1, Integer::sum);
        int penalty = Math.min((int) Math.pow(2, count - 1), 16);
        if (penalty > 0) {
            Bucket bucket = accountBuckets.computeIfAbsent(email, k -> buildAccountBucket());
            bucket.tryConsume(penalty); // drain extra tokens; ignore result
            log.debug("Auth failure #{} for email={}; draining {} tokens (backoff)", count, email, penalty);
        }
    }

    // -------------------------------------------------------------------------
    // Inner DTOs
    // -------------------------------------------------------------------------

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    static class RegisterRequest {
        @NotBlank(message = "Username is required")
        @Size(min = 3, max = 50, message = "Username must be between 3 and 50 characters")
        private String username;

        @NotBlank(message = "Email is required")
        @Email(message = "Email must be valid")
        @Size(max = 254, message = "Email cannot exceed 254 characters")
        private String email;

        @NotBlank(message = "Password is required")
        @Size(min = 8, max = 100, message = "Password must be between 8 and 100 characters")
        private String password;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    static class LoginRequest {
        @NotBlank(message = "Email is required")
        @Email(message = "Email must be valid")
        @Size(max = 254, message = "Email cannot exceed 254 characters")
        private String email;

        @NotBlank(message = "Password is required")
        @Size(max = 100, message = "Password cannot exceed 100 characters")
        private String password;
    }

    @Data
    @NoArgsConstructor
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
