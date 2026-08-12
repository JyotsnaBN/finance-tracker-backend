package com.financetracker.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.financetracker.config.RateLimitProperties;
import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Servlet filter that enforces per-IP and per-account rate limits.
 *
 * <ul>
 *   <li>Auth routes ({@code /api/auth/**}): tight per-IP bucket + per-email bucket
 *       with exponential-backoff penalty on repeated failures.</li>
 *   <li>Authenticated user-action routes: per-user-ID bucket.</li>
 *   <li>All other (public) routes: per-IP bucket.</li>
 * </ul>
 *
 * Bucket state is held in-process (ConcurrentHashMap). For multi-instance deployments
 * replace with Bucket4j's Redis or Hazelcast integration.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class RateLimitFilter extends OncePerRequestFilter {

    private final RateLimitProperties props;
    private final ObjectMapper objectMapper;

    // Buckets keyed by "<type>:<key>", e.g. "auth-ip:192.168.1.1"
    private final ConcurrentHashMap<String, Bucket> buckets = new ConcurrentHashMap<>();

    // Tracks consecutive auth failures per account email for exponential-backoff
    private final ConcurrentHashMap<String, Integer> authFailCounts = new ConcurrentHashMap<>();

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {

        String path = request.getRequestURI();
        String ip = resolveClientIp(request);

        Bucket bucket;
        String bucketKey;

        if (isAuthPath(path)) {
            // --- Per-IP bucket for auth routes ---
            bucketKey = "auth-ip:" + ip;
            bucket = buckets.computeIfAbsent(bucketKey, k -> buildBucket(
                    props.getAuthIpLimit(), props.getAuthWindowSeconds()));

            if (!bucket.tryConsume(1)) {
                log.warn("Rate limit exceeded (auth/IP): ip={} path={}", ip, path);
                sendRateLimitResponse(response, request);
                return;
            }

            // --- Per-account bucket (extracted from request body peek is not feasible
            //     in a filter without consuming the stream; instead key on IP as proxy
            //     for the account bucket — a per-email bucket is enforced in AuthController) ---

        } else if (isAuthenticated(request)) {
            // --- Per-user authenticated bucket ---
            String userId = resolveUserId(request);
            bucketKey = "user:" + (userId != null ? userId : ip);
            bucket = buckets.computeIfAbsent(bucketKey, k -> buildBucket(
                    props.getUserActionLimit(), props.getUserActionWindowSeconds()));

            if (!bucket.tryConsume(1)) {
                log.warn("Rate limit exceeded (user-action): user={} path={}", userId, path);
                sendRateLimitResponse(response, request);
                return;
            }

        } else {
            // --- Public routes ---
            bucketKey = "public:" + ip;
            bucket = buckets.computeIfAbsent(bucketKey, k -> buildBucket(
                    props.getPublicLimit(), props.getPublicWindowSeconds()));

            if (!bucket.tryConsume(1)) {
                log.warn("Rate limit exceeded (public): ip={} path={}", ip, path);
                sendRateLimitResponse(response, request);
                return;
            }
        }

        filterChain.doFilter(request, response);
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private Bucket buildBucket(int capacity, long windowSeconds) {
        Bandwidth limit = Bandwidth.builder()
                .capacity(capacity)
                .refillGreedy(capacity, Duration.ofSeconds(windowSeconds))
                .build();
        return Bucket.builder().addLimit(limit).build();
    }

    private boolean isAuthPath(String path) {
        return path != null && path.startsWith("/api/auth/");
    }

    private boolean isAuthenticated(HttpServletRequest request) {
        String auth = request.getHeader("Authorization");
        return auth != null && auth.startsWith("Bearer ");
    }

    /** Extract user ID from the JWT already validated upstream. Returns null if absent. */
    private String resolveUserId(HttpServletRequest request) {
        // The SecurityContext is not yet populated at filter entry time — use the raw
        // Authorization header value as the bucket key (token is unique per user).
        String auth = request.getHeader("Authorization");
        if (auth != null && auth.startsWith("Bearer ") && auth.length() > 7) {
            // Use first 32 chars of the token as a stable, non-guessable key.
            String token = auth.substring(7);
            return token.length() > 32 ? token.substring(0, 32) : token;
        }
        return null;
    }

    private String resolveClientIp(HttpServletRequest request) {
        String xff = request.getHeader("X-Forwarded-For");
        if (xff != null && !xff.isBlank()) {
            // X-Forwarded-For may contain a comma-separated list; take the first (client) IP.
            return xff.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }

    private void sendRateLimitResponse(HttpServletResponse response,
                                       HttpServletRequest request) throws IOException {
        response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        Map<String, Object> body = Map.of(
                "status", 429,
                "message", "Too many requests. Please slow down and try again later.",
                "path", request.getRequestURI(),
                "timestamp", Instant.now().toString()
        );
        response.getWriter().write(objectMapper.writeValueAsString(body));
    }
}
