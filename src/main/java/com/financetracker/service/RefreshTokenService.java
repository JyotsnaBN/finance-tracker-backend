package com.financetracker.service;

import com.financetracker.model.RefreshToken;
import com.financetracker.repository.RefreshTokenRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class RefreshTokenService {

    private final RefreshTokenRepository refreshTokenRepository;

    @Value("${jwt.refresh-expiration:604800000}")
    private long refreshExpiration;

    /**
     * Creates and persists a new refresh token for {@code userId}.
     * Any previous tokens for the user remain valid until they expire or are revoked.
     */
    @Transactional
    public RefreshToken createRefreshToken(UUID userId) {
        RefreshToken token = RefreshToken.builder()
                .token(UUID.randomUUID())
                .userId(userId)
                .expiresAt(Instant.now().plusMillis(refreshExpiration))
                .revoked(false)
                .build();
        return refreshTokenRepository.save(token);
    }

    /**
     * Validates a refresh token value.
     *
     * @throws RuntimeException if not found, expired, or already revoked.
     */
    @Transactional
    public RefreshToken validateRefreshToken(UUID tokenValue) {
        RefreshToken token = refreshTokenRepository.findByToken(tokenValue)
                .orElseThrow(() -> new RuntimeException("Refresh token not found"));

        if (token.isRevoked() || token.getExpiresAt().isBefore(Instant.now())) {
            token.setRevoked(true);
            refreshTokenRepository.save(token);
            throw new RuntimeException("Refresh token expired or revoked");
        }

        return token;
    }

    /**
     * Revokes a refresh token by its value.  No-op if not found.
     */
    @Transactional
    public void revokeRefreshToken(UUID tokenValue) {
        refreshTokenRepository.findByToken(tokenValue).ifPresent(token -> {
            token.setRevoked(true);
            refreshTokenRepository.save(token);
            log.info("Refresh token revoked for user {}", token.getUserId());
        });
    }
}
