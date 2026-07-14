package com.financetracker.security;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.UUID;

/**
 * Utility class for extracting the authenticated user's ID from the JWT-backed
 * SecurityContext. The principal is a UUID set by {@link JwtAuthenticationFilter}.
 */
public final class SecurityUtils {

    private SecurityUtils() {}

    /**
     * Returns the UUID of the currently authenticated user.
     *
     * @throws RuntimeException if there is no authenticated user in the current context
     */
    public static UUID getAuthenticatedUserId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new RuntimeException("User not authenticated");
        }
        Object principal = authentication.getPrincipal();
        if (principal instanceof UUID id) {
            return id;
        }
        return UUID.fromString(authentication.getName());
    }
}
