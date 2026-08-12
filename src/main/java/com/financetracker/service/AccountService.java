package com.financetracker.service;

import com.financetracker.dto.AccountDTO;
import com.financetracker.exception.DuplicateResourceException;
import com.financetracker.model.Account;
import com.financetracker.model.AccountType;
import com.financetracker.model.User;
import com.financetracker.repository.AccountRepository;
import com.financetracker.repository.UserRepository;
import com.financetracker.util.EncryptionUtil;
import com.financetracker.util.EntityMapper;
import com.financetracker.dto.BalanceHistoryDTO;
import com.financetracker.dto.SpendingTrendsDTO;
import com.financetracker.model.Transaction;
import com.financetracker.model.TransactionType;
import com.financetracker.repository.TransactionRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import java.util.ArrayList;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Collections;
import org.springframework.data.domain.Pageable;
import java.time.Instant;

@Service
@RequiredArgsConstructor
@Slf4j
public class AccountService {
    private final AccountRepository accountRepository;
    private final UserRepository userRepository;
    private final EntityMapper entityMapper;
    private final TransactionRepository transactionRepository;
    private final EncryptionUtil encryptionUtil;
    
    @Transactional(readOnly = true)
    public List<AccountDTO> getAllAccounts() {
        log.debug("Fetching all accounts");
        List<Account> accounts = accountRepository.findAll();
        return entityMapper.toAccountDTOList(accounts);
    }
    
    @Transactional(readOnly = true)
    public List<AccountDTO> getAccountsByUserId(UUID userId) {
        log.debug("Fetching accounts for user: {}", userId);
        List<Account> accounts = accountRepository.findByUserId(userId);
        return entityMapper.toAccountDTOList(accounts);
    }
    
    @Transactional(readOnly = true)
    public AccountDTO getAccountById(UUID id) {
        log.debug("Fetching account with id: {}", id);
        Account account = accountRepository.findById(id)
            .orElseThrow(() -> new RuntimeException("Account not found"));
        return entityMapper.toAccountDTO(account);
    }
    
    @Transactional
    public AccountDTO createAccount(AccountDTO dto) {
        log.info("Creating account: {} for user: {}", dto.getAccountName(), dto.getUserId());
        
        User user = userRepository.findById(dto.getUserId())
            .orElseThrow(() -> new RuntimeException("User not found"));

        // Encrypt before the duplicate check so the stored value matches the ciphertext.
        String encryptedAccountNumber = encryptionUtil.encryptIfPresent(dto.getAccountNumber());

        if (encryptedAccountNumber != null &&
            accountRepository.existsByAccountNumber(encryptedAccountNumber)) {
            throw new DuplicateResourceException("An account with this number already exists");
        }
        
        try {
            Account account = Account.builder()
                .user(user)
                .accountName(dto.getAccountName())
                .accountNumber(encryptedAccountNumber)
                .bankName(dto.getBankName())
                .accountType(dto.getAccountType())
                .currentBalance(dto.getCurrentBalance() != null ? dto.getCurrentBalance() : BigDecimal.ZERO)
                .isActive(dto.getIsActive() != null ? dto.getIsActive() : true)
                .build();
            
            Account saved = accountRepository.save(account);
            log.info("Account created successfully with id: {}", saved.getId());
            
            return entityMapper.toAccountDTO(saved);
        } catch (Exception e) {
            log.error("Failed to create account for user {}: {}", dto.getUserId(), e.getMessage(), e);
            throw new RuntimeException("Failed to create account", e);
        }
    }
    
    @Transactional
    public AccountDTO updateAccount(UUID id, AccountDTO dto) {
        log.info("Updating account with id: {}", id);
        
        Account existing = accountRepository.findById(id)
            .orElseThrow(() -> new RuntimeException("Account not found"));
        
        try {
            existing.setAccountName(dto.getAccountName());
            existing.setBankName(dto.getBankName());
            existing.setAccountType(dto.getAccountType());
            existing.setCurrentBalance(dto.getCurrentBalance());
            existing.setIsActive(dto.getIsActive());
            
            Account updated = accountRepository.save(existing);
            log.info("Account updated successfully: {}", id);
            
            return entityMapper.toAccountDTO(updated);
        } catch (Exception e) {
            log.error("Failed to update account {}: {}", id, e.getMessage(), e);
            throw new RuntimeException("Failed to update account", e);
        }
    }
    
    /**
     * Applies a transaction's monetary effect to an account's balance.
     *
     * For SAVINGS / CURRENT / WALLET accounts:
     *   CREDIT increases currentBalance; DEBIT decreases it.
     *
     * For CREDIT_CARD accounts, currentBalance represents the *available limit*:
     *   DEBIT (a spend) decreases available limit; CREDIT (a payment/refund) increases it.
     *
     * @param account  the account whose balance should be adjusted (will be saved)
     * @param amount   the absolute transaction amount (always positive)
     * @param type     DEBIT or CREDIT
     * @param reverse  true when undoing a previously applied transaction (update/delete)
     */
    @Transactional
    public void applyTransactionDelta(Account account, BigDecimal amount,
                                      TransactionType type, boolean reverse) {
        if (account == null || amount == null || type == null) {
            log.warn("applyTransactionDelta called with null argument — skipping");
            return;
        }

        BigDecimal current = account.getCurrentBalance() != null
                ? account.getCurrentBalance() : BigDecimal.ZERO;

        // For all account types: CREDIT adds to the balance figure, DEBIT subtracts.
        // CREDIT_CARD's currentBalance IS the available limit, so the same direction holds
        // (spending reduces available limit = DEBIT subtract; payment restores it = CREDIT add).
        BigDecimal delta = (type == TransactionType.CREDIT) ? amount : amount.negate();

        if (reverse) {
            delta = delta.negate();
        }

        account.setCurrentBalance(current.add(delta));
        accountRepository.save(account);
        log.debug("Balance updated for account {}: {} → {} (type={}, reverse={})",
                account.getId(), current, account.getCurrentBalance(), type, reverse);
    }

    /**
     * Recalculates an account's currentBalance from scratch by replaying all
     * non-deleted transactions recorded on or after the account's creation date.
     * The user-supplied balance at registration time is treated as the opening balance.
     *
     * Safe to call at any time; idempotent.
     */
    @Transactional
    public AccountDTO recalculateBalance(UUID accountId) {
        Account account = accountRepository.findById(accountId)
                .orElseThrow(() -> new RuntimeException("Account not found"));

        // Re-fetch all non-deleted transactions for this account since it was created
        Instant openingDate = account.getCreatedAt();
        Instant now = Instant.now();

        List<Transaction> transactions = new ArrayList<>(
                transactionRepository.findByAccountIdAndTransactionDateBetween(
                        account.getId(), openingDate, now, Pageable.unpaged()).getContent());

        // Sum credits and debits independently
        BigDecimal totalCredit = transactions.stream()
                .filter(t -> t.getTransactionType() == TransactionType.CREDIT)
                .map(Transaction::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal totalDebit = transactions.stream()
                .filter(t -> t.getTransactionType() == TransactionType.DEBIT)
                .map(Transaction::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        // Opening balance (what the user entered at registration) + net movement
        BigDecimal openingBalance = account.getCurrentBalance() != null
                ? account.getCurrentBalance() : BigDecimal.ZERO;

        // We cannot recover the true opening balance once transactions have already
        // been applied, so we recalculate purely from transaction history.
        // net = credits - debits (same direction logic as applyTransactionDelta)
        BigDecimal recalculated = totalCredit.subtract(totalDebit);

        account.setCurrentBalance(recalculated);
        Account saved = accountRepository.save(account);
        log.info("Balance recalculated for account {}: {} (credits={}, debits={})",
                accountId, recalculated, totalCredit, totalDebit);

        return entityMapper.toAccountDTO(saved);
    }

    @Transactional
    public void deleteAccount(UUID id) {
        log.info("Deleting account with id: {}", id);
        
        if (!accountRepository.existsById(id)) {
            throw new RuntimeException("Account not found");
        }
        
        try {
            accountRepository.deleteById(id);
            log.info("Account deleted successfully: {}", id);
        } catch (Exception e) {
            log.error("Failed to delete account {}: {}", id, e.getMessage(), e);
            throw new RuntimeException("Failed to delete account. It may be referenced by existing transactions.", e);
        }
    }

    @Transactional(readOnly = true)
    public BalanceHistoryDTO getBalanceHistory(UUID accountId, LocalDate startDate, LocalDate endDate, String interval) {
        log.debug("Fetching balance history for account: {}, interval: {}", accountId, interval);
        
        Account account = accountRepository.findById(accountId)
            .orElseThrow(() -> new RuntimeException("Account not found"));
        
        if (startDate == null) {
            startDate = LocalDate.now().minusMonths(3);
        }
        if (endDate == null) {
            endDate = LocalDate.now();
        }
        
        List<BalanceHistoryDTO.BalanceDataPoint> dataPoints = calculateBalanceHistory(
            account, startDate, endDate, interval != null ? interval : "DAY");
        
        return BalanceHistoryDTO.builder()
            .accountId(accountId)
            .accountName(account.getAccountName())
            .interval(interval != null ? interval : "DAY")
            .data(dataPoints)
            .build();
    }

    private List<BalanceHistoryDTO.BalanceDataPoint> calculateBalanceHistory(
            Account account, LocalDate startDate, LocalDate endDate, String interval) {
        
        List<BalanceHistoryDTO.BalanceDataPoint> dataPoints = new ArrayList<>();
        BigDecimal currentBalance = account.getCurrentBalance();
        
        Instant startInstant = startDate.atStartOfDay(ZoneId.systemDefault()).toInstant();
        Instant endInstant = endDate.atTime(23, 59, 59).atZone(ZoneId.systemDefault()).toInstant();
        
        List<Transaction> transactions = new ArrayList<>(transactionRepository
            .findByAccountIdAndTransactionDateBetween(account.getId(), startInstant, endInstant, 
                Pageable.unpaged())
            .getContent());

        transactions.sort((t1, t2) -> t2.getTransactionDate().compareTo(t1.getTransactionDate()));
        
        LocalDate currentDate = endDate;
        int transactionIndex = 0;
        
        while (!currentDate.isBefore(startDate)) {
            BigDecimal calculatedBalance = currentBalance;
            BigDecimal actualAvailableLimit = null;
            boolean hasActualLimit = false;
            
            while (transactionIndex < transactions.size()) {
                Transaction t = transactions.get(transactionIndex);
                LocalDate transactionDate = LocalDate.ofInstant(t.getTransactionDate(), ZoneId.systemDefault());
                
                if (transactionDate.isAfter(currentDate)) {
                    if (t.getTransactionType() == TransactionType.CREDIT) {
                        calculatedBalance = calculatedBalance.subtract(t.getAmount());
                    } else {
                        calculatedBalance = calculatedBalance.add(t.getAmount());
                    }
                    transactionIndex++;
                } else if (transactionDate.equals(currentDate)) {
                    if (t.getAvailableLimitAtTransaction() != null) {
                        actualAvailableLimit = t.getAvailableLimitAtTransaction();
                        hasActualLimit = true;
                    }
                    break;
                } else {
                    break;
                }
            }
            
            BigDecimal finalBalance = hasActualLimit ? actualAvailableLimit : calculatedBalance;
            
            dataPoints.add(BalanceHistoryDTO.BalanceDataPoint.builder()
                .date(currentDate)
                .balance(finalBalance)
                .availableLimit(actualAvailableLimit)
                .hasActualLimit(hasActualLimit)
                .build());
            
            currentDate = switch (interval.toUpperCase()) {
                case "WEEK" -> currentDate.minusWeeks(1);
                case "MONTH" -> currentDate.minusMonths(1);
                default -> currentDate.minusDays(1);
            };
        }
        
        for (int i = 0; i < dataPoints.size(); i++) {
            BalanceHistoryDTO.BalanceDataPoint point = dataPoints.get(i);
            if (point.getAvailableLimit() == null) {
                BigDecimal interpolatedLimit = null;
                for (int j = i + 1; j < dataPoints.size(); j++) {
                    if (dataPoints.get(j).getAvailableLimit() != null) {
                        interpolatedLimit = dataPoints.get(j).getAvailableLimit();
                        break;
                    }
                }
                
                if (interpolatedLimit == null) {
                    for (int j = i - 1; j >= 0; j--) {
                        if (dataPoints.get(j).getAvailableLimit() != null) {
                            interpolatedLimit = dataPoints.get(j).getAvailableLimit();
                            break;
                        }
                    }
                }
                
                if (interpolatedLimit != null) {
                    point.setAvailableLimit(interpolatedLimit);
                    point.setBalance(interpolatedLimit);
                }
            }
        }
        
        Collections.reverse(dataPoints);
        return dataPoints;
    }
}
