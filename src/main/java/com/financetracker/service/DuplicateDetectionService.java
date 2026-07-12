package com.financetracker.service;

import com.financetracker.model.Transaction;
import com.financetracker.repository.TransactionRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.UUID;

@Service
@Slf4j
public class DuplicateDetectionService {
    
    @Autowired
    private TransactionRepository transactionRepository;

    public boolean isDuplicate(Transaction transaction, UUID userId, String rawText) {
        if (rawText != null && !rawText.trim().isEmpty()) {
            boolean duplicateByRawText = transactionRepository
                .existsByAccountUserIdAndRawText(userId, rawText);
            if (duplicateByRawText) {
                log.debug("Duplicate found by rawText for user: {}", userId);
                return true;
            }
        }
        
        if (transaction.getAccount() != null && transaction.getAmount() != null &&
            transaction.getTransactionType() != null && transaction.getTransactionDate() != null) {
            
            boolean exactMatch = transactionRepository.existsByAccountIdAndAmountAndTypeAndDate(
                transaction.getAccount().getId(),
                transaction.getAmount(),
                transaction.getTransactionType(),
                transaction.getTransactionDate()
            );
            
            if (exactMatch) {
                log.debug("Duplicate found by exact match for account: {}", 
                    transaction.getAccount().getId());
                return true;
            }
            
            Instant startDate = transaction.getTransactionDate().minusSeconds(300);
            Instant endDate = transaction.getTransactionDate().plusSeconds(300);
            
            boolean similarTransaction = transactionRepository.existsSimilarTransaction(
                transaction.getAccount().getId(),
                transaction.getAmount(),
                transaction.getTransactionType(),
                startDate,
                endDate
            );
            
            if (similarTransaction) {
                log.debug("Similar transaction found within 5 minutes for account: {}", 
                    transaction.getAccount().getId());
                return true;
            }
        }
        
        return false;
    }

    public String generateTransactionHash(Transaction transaction) {
        String hashInput = String.format("%s|%s|%s|%s",
            transaction.getAccount().getId(),
            transaction.getAmount(),
            transaction.getTransactionType(),
            transaction.getTransactionDate().getEpochSecond()
        );
        
        return Integer.toHexString(hashInput.hashCode());
    }
}
