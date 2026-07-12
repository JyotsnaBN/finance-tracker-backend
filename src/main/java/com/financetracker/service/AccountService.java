package com.financetracker.service;

import com.financetracker.dto.AccountDTO;
import com.financetracker.exception.DuplicateResourceException;
import com.financetracker.model.Account;
import com.financetracker.model.User;
import com.financetracker.repository.AccountRepository;
import com.financetracker.repository.UserRepository;
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
        
        if (dto.getAccountNumber() != null &&
            accountRepository.existsByAccountNumber(dto.getAccountNumber())) {
            throw new DuplicateResourceException("An account with this number already exists");
        }
        
        try {
            Account account = Account.builder()
                .user(user)
                .accountName(dto.getAccountName())
                .accountNumber(dto.getAccountNumber())
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
