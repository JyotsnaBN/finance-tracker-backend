package com.financetracker.service;

import com.financetracker.dto.ParsedSmsResultDTO;
import com.financetracker.dto.TransactionDTO;
import com.financetracker.model.*;
import com.financetracker.repository.FailedTransactionRepository;
import com.financetracker.repository.TransactionRepository;
import com.financetracker.repository.UserRepository;
import com.financetracker.util.EntityMapper;
import com.financetracker.util.TransactionParsingUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

/**
 * Orchestrates the SMS ingest pipeline:
 *   parse → resolve account → categorize → duplicate check → save transaction
 *
 * If account resolution fails, a {@link FailedTransaction} is saved and an exception is thrown
 * so the controller can return HTTP 422.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class SmsIngestionService {

    private final SmsParserService smsParserService;
    private final AccountResolutionService accountResolutionService;
    private final CategoryService categoryService;
    private final DuplicateDetectionService duplicateDetectionService;
    private final TransactionRepository transactionRepository;
    private final FailedTransactionRepository failedTransactionRepository;
    private final UserRepository userRepository;
    private final EntityMapper entityMapper;

    /**
     * Ingests a raw SMS body for {@code userId}.
     *
     * @return a {@link TransactionDTO} representing the saved transaction.
     * @throws IllegalArgumentException if the SMS cannot be parsed (requires manual review).
     * @throws RuntimeException         if account resolution fails (422 caller-side).
     */
    @Transactional
    public TransactionDTO ingest(UUID userId, String smsBody, String sender) {
        log.info("SMS ingest for user {}", userId);

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found: " + userId));

        // Step 1 — parse
        ParsedSmsResultDTO parsed = smsParserService.parseSms(smsBody);
        if (parsed == null || parsed.getAmount() == null || parsed.getTransactionType() == null) {
            String reason = "SMS could not be parsed: no amount or transaction type extracted";
            log.warn("{} for user {}: {}", reason, userId, smsBody);
            saveFailedTransaction(user, smsBody, sender, reason, parsed);
            throw new RuntimeException(reason);
        }

        // Step 2 — resolve account
        Account account = accountResolutionService.resolveAccount(parsed.getAccountHint(), user);
        if (account == null) {
            String reason = "Account could not be resolved from hint: " + parsed.getAccountHint();
            log.warn("{} for user {}", reason, userId);
            saveFailedTransaction(user, smsBody, sender, reason, parsed);
            throw new RuntimeException(reason);
        }

        // Step 3 — categorize
        Category category = categoryService.categorizeTransaction(parsed.getDescription());

        // Step 4 — build entity
        String safeDescription = TransactionParsingUtil.truncateString(
                parsed.getDescription() != null ? parsed.getDescription() : "SMS transaction", 255);

        Transaction transaction = Transaction.builder()
                .account(account)
                .category(category)
                .amount(parsed.getAmount())
                .transactionType(parsed.getTransactionType())
                .source(TransactionSource.SMS)
                .description(safeDescription)
                .rawText(smsBody)
                .transactionDate(parsed.getTransactionDate() != null ? parsed.getTransactionDate() : Instant.now())
                .availableLimitAtTransaction(parsed.getAvailableLimit())
                .build();

        // Step 5 — duplicate check
        if (duplicateDetectionService.isDuplicate(transaction, userId, smsBody)) {
            throw new RuntimeException("Duplicate transaction detected for SMS");
        }

        // Step 6 — save
        Transaction saved = transactionRepository.save(transaction);
        log.info("SMS transaction saved: id={} for user {}", saved.getId(), userId);
        return entityMapper.toTransactionDTO(saved);
    }

    private void saveFailedTransaction(User user, String smsBody, String sender,
                                       String reason, ParsedSmsResultDTO parsed) {
        try {
            FailedTransaction failed = FailedTransaction.builder()
                    .user(user)
                    .emailAccount(null)       // SMS-originated — no email account
                    .rawEmailContent(smsBody)
                    .failureReason(TransactionParsingUtil.truncateString(reason, 255))
                    .accountHint(parsed != null
                            ? TransactionParsingUtil.truncateString(parsed.getAccountHint(), 100)
                            : null)
                    .amount(parsed != null ? parsed.getAmount() : null)
                    .transactionType(parsed != null ? parsed.getTransactionType() : null)
                    .transactionDate(parsed != null && parsed.getTransactionDate() != null
                            ? parsed.getTransactionDate()
                            : Instant.now())
                    .description(parsed != null
                            ? TransactionParsingUtil.truncateString(parsed.getDescription(), 255)
                            : null)
                    .requiresManualReview(true)
                    .resolved(false)
                    .build();
            failedTransactionRepository.save(failed);
            log.info("Saved failed SMS transaction for user {}: {}", user.getId(), reason);
        } catch (Exception e) {
            log.error("Could not save failed SMS transaction for user {}: {}", user.getId(), e.getMessage());
        }
    }
}
