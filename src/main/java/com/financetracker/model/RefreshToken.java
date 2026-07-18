package com.financetracker.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "refresh_tokens")
@Getter @Setter @Builder
@NoArgsConstructor @AllArgsConstructor
public class RefreshToken {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    @Column(columnDefinition = "uuid")
    private UUID id;

    /** The opaque token value presented by the client. Unique, not updatable. */
    @Column(name = "token", nullable = false, unique = true, updatable = false, columnDefinition = "uuid")
    private UUID token;

    /** Owner of this refresh token. */
    @Column(name = "user_id", nullable = false, updatable = false, columnDefinition = "uuid")
    private UUID userId;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Builder.Default
    @Column(name = "revoked", nullable = false)
    private boolean revoked = false;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
}
