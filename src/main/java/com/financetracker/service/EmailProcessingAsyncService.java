package com.financetracker.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class EmailProcessingAsyncService {

    private final EmailReaderService emailReaderService;

    @Async
    public void processAllEmailsAsync() {
        try {
            log.info("Async email processing started");
            emailReaderService.processAllEmails();
            log.info("Async email processing completed");
        } catch (Exception e) {
            log.error("Async email processing failed", e);
        }
    }
}
