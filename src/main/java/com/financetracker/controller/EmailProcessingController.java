package com.financetracker.controller;

import com.financetracker.service.EmailProcessingAsyncService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.Map;

@RestController
@RequestMapping("/api/internal/email-processing")
@RequiredArgsConstructor
@Slf4j
public class EmailProcessingController {

    private final EmailProcessingAsyncService emailProcessingAsyncService;

    @Value("${internal.api.key}")
    private String internalApiKey;

    @PostMapping
    public ResponseEntity<?> processAllEmails(
            @RequestHeader(value = "X-API-KEY", required = false) String apiKey) {

        // Constant-time comparison — prevents timing-oracle attacks on the API key.
        if (apiKey == null || !MessageDigest.isEqual(
                apiKey.getBytes(StandardCharsets.UTF_8),
                internalApiKey.getBytes(StandardCharsets.UTF_8))) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of(
                            "message", "Invalid API key",
                            "timestamp", Instant.now().toString()
                    ));
        }

        try {
            log.info("Internal async email processing API triggered");
            emailProcessingAsyncService.processAllEmailsAsync();

            return ResponseEntity.status(HttpStatus.ACCEPTED)
                    .body(Map.of(
                            "message", "Email processing accepted",
                            "status", "started",
                            "flow", "transactions -> delivery/order",
                            "timestamp", Instant.now().toString()
                    ));
        } catch (Exception e) {
            // Log full exception server-side; return a generic message to the caller
            // so internal details (paths, schema names) are never leaked.
            log.error("Failed to start async email processing", e);

            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of(
                            "message", "Failed to start email processing",
                            "error", "Failed to start email processing. See server logs for details.",
                            "timestamp", Instant.now().toString()
                    ));
        }
    }
}
