package com.financetracker.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * All rate-limit thresholds are externalised here and read from
 * application.properties / environment variables — nothing is hardcoded.
 */
@Component
@ConfigurationProperties(prefix = "rate-limit")
@Data
public class RateLimitProperties {

    /** Auth routes: max requests per IP within the window. */
    private int authIpLimit = 10;

    /** Auth routes: window duration in seconds for per-IP bucket. */
    private long authWindowSeconds = 60;

    /** Auth routes: max requests per account (email) within the window. */
    private int authAccountLimit = 5;

    /** Auth routes: window duration in seconds for per-account bucket. */
    private long authAccountWindowSeconds = 60;

    /** Authenticated user-action routes: max requests per user per minute. */
    private int userActionLimit = 200;

    /** Authenticated user-action routes: window duration in seconds. */
    private long userActionWindowSeconds = 60;

    /** Public (non-auth) routes: max requests per IP per minute. */
    private int publicLimit = 60;

    /** Public routes: window duration in seconds. */
    private long publicWindowSeconds = 60;
}
