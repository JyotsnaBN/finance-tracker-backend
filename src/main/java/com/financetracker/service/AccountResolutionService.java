package com.financetracker.service;

import com.financetracker.model.Account;
import com.financetracker.model.AccountType;
import com.financetracker.model.User;
import com.financetracker.repository.AccountRepository;
import com.financetracker.util.EncryptionUtil;
import com.financetracker.util.TransactionParsingUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

/**
 * Resolves a raw account hint string (e.g. "XX1234", "HDFC Savings") to a stored Account entity.
 * Extracted from EmailReaderService so the same logic can be shared with SmsIngestionService.
 *
 * Resolution order:
 *  1. Last-4 digit match — decrypts each stored account number and compares in memory.
 *  2. Bank name + account type match.
 *  3. Account type-only match (only if the user has exactly one account of that type).
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AccountResolutionService {

    private final AccountRepository accountRepository;
    private final EncryptionUtil encryptionUtil;

    /**
     * Attempts to resolve {@code accountHint} to an Account owned by {@code user}.
     *
     * @return the matched Account, or {@code null} if no match could be made.
     */
    public Account resolveAccount(String accountHint, User user) {
        if (!TransactionParsingUtil.isValidString(accountHint)) {
            log.warn("Empty or null account hint for user: {}", user != null ? user.getId() : "null");
            return null;
        }

        if (user == null) {
            log.error("User is null in resolveAccount");
            return null;
        }

        log.debug("Attempting to resolve account for user {} with hint: {}", user.getId(), accountHint);

        try {
            // Tier 1: last-4 digit match (in-memory, after decrypting stored account numbers)
            String last4Digits = TransactionParsingUtil.extractLast4Digits(accountHint);
            if (last4Digits != null) {
                log.debug("Extracted last 4 digits: {}", last4Digits);
                List<Account> userAccounts = accountRepository.findByUserId(user.getId());
                for (Account account : userAccounts) {
                    String storedNumber = account.getAccountNumber();
                    if (storedNumber == null) continue;
                    try {
                        String decrypted = encryptionUtil.decryptIfPresent(storedNumber);
                        if (decrypted != null && decrypted.endsWith(last4Digits)) {
                            log.info("Account resolved by number for user {}: last4={}, accountId={}",
                                    user.getId(), last4Digits, account.getId());
                            return account;
                        }
                    } catch (Exception e) {
                        log.warn("Failed to decrypt account number for account {}: {}", account.getId(), e.getMessage());
                    }
                }
                log.debug("No account found with last 4 digits: {}", last4Digits);
            }

            // Tier 2: bank name + account type
            String bankName = TransactionParsingUtil.extractBankName(accountHint);
            AccountType accountType = TransactionParsingUtil.extractAccountType(accountHint);

            log.debug("Extracted bank: {}, type: {}", bankName, accountType);

            if (bankName != null && accountType != null) {
                Optional<Account> account = accountRepository
                        .findByUserIdAndBankNameAndAccountType(user.getId(), bankName, accountType);
                if (account.isPresent()) {
                    log.info("Account resolved by bank + type for user {}: bank={}, type={}, accountId={}",
                            user.getId(), bankName, accountType, account.get().getId());
                    return account.get();
                } else {
                    log.debug("No account found for bank {} and type {}", bankName, accountType);
                }
            }

            // Tier 3: account type only (unambiguous single account)
            if (accountType != null) {
                List<Account> accounts = accountRepository.findByUserIdAndAccountType(user.getId(), accountType);
                log.debug("Found {} accounts of type {} for user {}", accounts.size(), accountType, user.getId());
                if (accounts.size() == 1) {
                    log.info("Account resolved by type only for user {}: type={}, accountId={}",
                            user.getId(), accountType, accounts.get(0).getId());
                    return accounts.get(0);
                } else if (accounts.size() > 1) {
                    log.warn("Multiple accounts found for type {} - cannot auto-resolve", accountType);
                }
            }

            log.warn("Could not resolve account for user {} with hint: {} (last4={}, bank={}, type={})",
                    user.getId(), accountHint, last4Digits, bankName, accountType);
            return null;

        } catch (Exception e) {
            log.error("Error resolving account for user {} with hint '{}': {}",
                    user.getId(), accountHint, e.getMessage());
            return null;
        }
    }
}
