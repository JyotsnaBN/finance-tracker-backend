package com.financetracker.service;

import com.financetracker.dto.TransactionDTO;
import com.financetracker.model.Account;
import com.financetracker.model.Category;
import com.financetracker.model.Transaction;
import com.financetracker.repository.AccountRepository;
import com.financetracker.repository.CategoryRepository;
import com.financetracker.repository.TransactionRepository;
import com.financetracker.security.SecurityUtils;
import com.financetracker.util.EntityMapper;
import com.financetracker.util.TransactionParsingUtil;
import com.financetracker.dto.BulkTransactionRequestDTO;
import com.financetracker.dto.BulkTransactionResponseDTO;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.ArrayList;

@Service
@RequiredArgsConstructor
@Slf4j
public class TransactionService {
    private final TransactionRepository transactionRepository;
    private final AccountRepository accountRepository;
    private final CategoryRepository categoryRepository;
    private final CategoryService categoryService;
    private final EntityMapper entityMapper;
    
    @Transactional(readOnly = true)
    public List<TransactionDTO> getAllTransactions() {
        log.debug("Fetching all transactions");
        List<Transaction> transactions = transactionRepository.findAll();
        return entityMapper.toTransactionDTOList(transactions);
    }
    
    @Transactional(readOnly = true)
    public List<TransactionDTO> getTransactionsByUserId(UUID userId) {
        log.debug("Fetching transactions for user: {}", userId);
        List<Transaction> transactions = transactionRepository.findByAccountUserId(userId);
        return entityMapper.toTransactionDTOList(transactions);
    }
    
    @Transactional(readOnly = true)
    public TransactionDTO getTransactionById(Long id) {
        log.debug("Fetching transaction with id: {}", id);
        Transaction transaction = transactionRepository.findById(id)
            .orElseThrow(() -> new RuntimeException("Transaction not found"));
        return entityMapper.toTransactionDTO(transaction);
    }
    
    @Transactional
    public TransactionDTO createTransaction(TransactionDTO dto) {
        log.info("Creating transaction for account: {}", dto.getAccountId());

        Account account;
        if (dto.getAccountId() != null) {
            account = accountRepository.findById(dto.getAccountId())
                .orElseThrow(() -> new RuntimeException("Account not found: " + dto.getAccountId()));
        } else {
            account = resolveAccountFromRawText(dto.getRawText());
            if (account == null) {
                throw new RuntimeException(
                    "Could not resolve account: accountId is null and no matching account " +
                    "found from rawText. Please register the account first.");
            }
            log.info("Account resolved from rawText: {}", account.getId());
        }

        Category category;
        if (dto.getCategoryId() != null) {
            category = categoryRepository.findById(dto.getCategoryId())
                .orElseThrow(() -> new RuntimeException("Category not found: " + dto.getCategoryId()));
        } else {
            category = categoryService.categorizeTransaction(dto.getDescription());
            log.info("Category auto-assigned: '{}' for description: '{}'",
                category.getName(), dto.getDescription());
        }
        
        if (isDuplicateTransaction(dto, account)) {
            log.warn("Duplicate transaction detected - Account: {}, Amount: {}, Type: {}, Date: {}",
                dto.getAccountId(), dto.getAmount(), dto.getTransactionType(), dto.getTransactionDate());
            throw new RuntimeException("Duplicate transaction: A similar transaction already exists");
        }
        
        try {
            Transaction transaction = Transaction.builder()
                .account(account)
                .category(category)
                .amount(dto.getAmount())
                .transactionType(dto.getTransactionType())
                .source(dto.getSource())
                .description(dto.getDescription())
                .rawText(dto.getRawText())
                .transactionDate(dto.getTransactionDate())
                .build();
            
            Transaction saved = transactionRepository.save(transaction);
            log.info("Transaction created successfully with id: {}", saved.getId());
            
            return entityMapper.toTransactionDTO(saved);
        } catch (Exception e) {
            log.error("Failed to create transaction for account {}: {}", dto.getAccountId(), e.getMessage(), e);
            throw new RuntimeException("Failed to create transaction", e);
        }
    }

    private Account resolveAccountFromRawText(String rawText) {
        if (rawText == null || rawText.isBlank()) return null;
        try {
            UUID userId = SecurityUtils.getAuthenticatedUserId();
            String last4 = TransactionParsingUtil.extractLast4Digits(rawText);
            if (last4 != null) {
                Optional<Account> found = accountRepository
                    .findByUserIdAndAccountNumberEndingWith(userId, last4);
                if (found.isPresent()) {
                    log.debug("Resolved account by last4={} for user {}", last4, userId);
                    return found.get();
                }
            }
            // Fallback: if user has exactly one account, use it
            List<Account> accounts = accountRepository.findByUserId(userId);
            if (accounts.size() == 1) {
                log.debug("Only one account for user {} — using it as fallback", userId);
                return accounts.get(0);
            }
            return null;
        } catch (Exception e) {
            log.error("Error resolving account from rawText: {}", e.getMessage());
            return null;
        }
    }

    private boolean isDuplicateTransaction(TransactionDTO dto, Account account) {
        try {
            if (dto.getRawText() != null && !dto.getRawText().trim().isEmpty()) {
                boolean duplicateByRawText = transactionRepository.existsByAccountUserIdAndRawText(
                    account.getUser().getId(), dto.getRawText());
                if (duplicateByRawText) {
                    log.debug("Duplicate found by rawText for user: {}", account.getUser().getId());
                    return true;
                }
            }
            
            boolean exactMatch = transactionRepository.existsByAccountIdAndAmountAndTypeAndDate(
                account.getId(), dto.getAmount(), dto.getTransactionType(), dto.getTransactionDate());
            if (exactMatch) {
                log.debug("Duplicate found by exact match for account: {}", account.getId());
                return true;
            }
            
            Instant startDate = dto.getTransactionDate().minusSeconds(300);
            Instant endDate = dto.getTransactionDate().plusSeconds(300);
            boolean similarTransaction = transactionRepository.existsSimilarTransaction(
                account.getId(), dto.getAmount(), dto.getTransactionType(),
                startDate, endDate);
            if (similarTransaction) {
                log.debug("Similar transaction found within 5 minutes for account: {}", account.getId());
                return true;
            }
            
            return false;
        } catch (Exception e) {
            log.error("Error checking for duplicate transaction: {}", e.getMessage(), e);
            return false;
        }
    }
    
    @Transactional
    public TransactionDTO updateTransaction(Long id, TransactionDTO dto) {
        log.info("Updating transaction with id: {}", id);
        
        Transaction existing = transactionRepository.findById(id)
            .orElseThrow(() -> new RuntimeException("Transaction not found"));
        
        if (dto.getCategoryId() != null &&
            !dto.getCategoryId().equals(existing.getCategory().getId())) {
            Category category = categoryRepository.findById(dto.getCategoryId())
                .orElseThrow(() -> new RuntimeException("Category not found"));
            existing.setCategory(category);
        }
        
        try {
            existing.setAmount(dto.getAmount());
            existing.setTransactionType(dto.getTransactionType());
            existing.setDescription(dto.getDescription());
            existing.setTransactionDate(dto.getTransactionDate());
            
            Transaction updated = transactionRepository.save(existing);
            log.info("Transaction updated successfully: {}", id);
            
            return entityMapper.toTransactionDTO(updated);
        } catch (Exception e) {
            log.error("Failed to update transaction {}: {}", id, e.getMessage(), e);
            throw new RuntimeException("Failed to update transaction", e);
        }
    }
    
    @Transactional
    public void deleteTransaction(Long id) {
        log.info("Deleting transaction with id: {}", id);
        
        if (!transactionRepository.existsById(id)) {
            throw new RuntimeException("Transaction not found");
        }
        
        try {
            transactionRepository.deleteById(id);
            log.info("Transaction deleted successfully: {}", id);
        } catch (Exception e) {
            log.error("Failed to delete transaction {}: {}", id, e.getMessage(), e);
            throw new RuntimeException("Failed to delete transaction", e);
        }
    }

    @Transactional
    public BulkTransactionResponseDTO createBulkTransactions(BulkTransactionRequestDTO request) {
        log.info("Creating bulk transactions - count: {}", request.getTransactions().size());
        
        List<TransactionDTO> createdTransactions = new ArrayList<>();
        List<String> errors = new ArrayList<>();
        int created = 0;
        int failed = 0;
        
        for (TransactionDTO dto : request.getTransactions()) {
            try {
                TransactionDTO createdTransaction = createTransaction(dto);
                createdTransactions.add(createdTransaction);
                created++;
            } catch (Exception e) {
                failed++;
                String errorMsg = String.format("Failed to create transaction for account %s, amount %s: %s",
                    dto.getAccountId(), dto.getAmount(), e.getMessage());
                errors.add(errorMsg);
                log.error(errorMsg, e);
            }
        }
        
        log.info("Bulk transaction creation completed - Created: {}, Failed: {}", created, failed);
        
        return BulkTransactionResponseDTO.builder()
            .created(created)
            .failed(failed)
            .transactions(createdTransactions)
            .errors(errors)
            .build();
    }
}
