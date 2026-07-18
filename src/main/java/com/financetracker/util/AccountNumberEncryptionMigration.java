package com.financetracker.util;

import com.financetracker.model.Account;
import com.financetracker.repository.AccountRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;

/**
 * One-time migration: encrypts any plain-text account numbers that were saved before
 * Sub-Task 4b introduced AES-256-GCM encryption at rest.
 *
 * <p>Detection heuristic: a Base64-encoded GCM ciphertext always starts with at least
 * 16 characters and contains only Base64 alphabet characters. Plain-text account numbers
 * (digits / masked digits like "XX1234") will not survive a decode-then-decrypt round-trip,
 * so we attempt to decrypt — if it succeeds the row is already encrypted; if it throws
 * we treat it as plain text and re-encrypt.</p>
 *
 * <p>Enable once by setting {@code migration.account-number.enabled=true} in
 * {@code application.properties}.  Disable after the first successful run.</p>
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class AccountNumberEncryptionMigration {

    private final AccountRepository accountRepository;
    private final EncryptionUtil encryptionUtil;
    private final TransactionTemplate transactionTemplate;

    @Value("${migration.account-number.enabled:false}")
    private boolean enabled;

    @EventListener(ApplicationReadyEvent.class)
    public void migrate() {
        if (!enabled) {
            log.debug("Account-number encryption migration is disabled — skipping");
            return;
        }

        log.info("Starting account-number encryption migration…");
        // Use TransactionTemplate rather than @Transactional: the ApplicationReadyEvent
        // fires after context refresh, at which point the startup EntityManager has already
        // been closed. TransactionTemplate forces a brand-new EntityManager/Session to be
        // opened here rather than reusing the stale one from the AOP proxy.
        transactionTemplate.executeWithoutResult(status -> {
            List<Account> accounts = accountRepository.findAll();
            int migrated = 0;
            int skipped = 0;

            for (Account account : accounts) {
                String stored = account.getAccountNumber();
                if (stored == null || stored.isBlank()) {
                    skipped++;
                    continue;
                }
                if (isAlreadyEncrypted(stored)) {
                    skipped++;
                    continue;
                }
                // Plain text — encrypt and save.
                account.setAccountNumber(encryptionUtil.encrypt(stored));
                accountRepository.save(account);
                migrated++;
            }

            log.info("Account-number encryption migration complete: {} migrated, {} skipped", migrated, skipped);
        });
    }

    /**
     * Returns {@code true} if {@code value} can be decrypted without throwing,
     * meaning it was already stored as ciphertext.
     */
    private boolean isAlreadyEncrypted(String value) {
        try {
            encryptionUtil.decrypt(value);
            return true;
        } catch (Exception e) {
            return false;
        }
    }
}
