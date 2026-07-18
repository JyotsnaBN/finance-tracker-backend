package com.financetracker.controller;

import com.financetracker.dto.SmsIngestRequestDTO;
import com.financetracker.dto.TransactionDTO;
import com.financetracker.security.SecurityUtils;
import com.financetracker.service.SmsIngestionService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/sms")
@RequiredArgsConstructor
@Slf4j
public class SmsController {

    private final SmsIngestionService smsIngestionService;

    /**
     * Accepts a raw SMS body forwarded from the Android app and processes it.
     *
     * <p>Returns {@code 201 Created} with the saved {@link TransactionDTO} on success,
     * or {@code 422 Unprocessable Entity} with an error body when the SMS could not be
     * parsed or the account could not be resolved.</p>
     */
    @PostMapping("/ingest")
    public ResponseEntity<?> ingestSms(@Valid @RequestBody SmsIngestRequestDTO request) {
        UUID userId = SecurityUtils.getAuthenticatedUserId();
        log.info("SMS ingest request from user {}", userId);
        try {
            TransactionDTO result = smsIngestionService.ingest(userId, request.getSmsBody(), request.getSender());
            return ResponseEntity.status(HttpStatus.CREATED).body(result);
        } catch (Exception e) {
            log.warn("SMS ingest failed for user {}: {}", userId, e.getMessage());
            return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY)
                    .body(Map.of(
                            "error", e.getMessage() != null ? e.getMessage() : "SMS could not be processed",
                            "timestamp", Instant.now().toString()
                    ));
        }
    }
}
