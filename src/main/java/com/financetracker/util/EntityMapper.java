package com.financetracker.util;

import com.financetracker.dto.AccountDTO;
import com.financetracker.dto.CategoryDTO;
import com.financetracker.dto.TransactionDTO;
import com.financetracker.dto.UserDTO;

import com.financetracker.model.Account;
import com.financetracker.model.Category;
import com.financetracker.model.Transaction;
import com.financetracker.model.User;
import com.financetracker.dto.DeliveryMetadataDTO;
import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;

import org.springframework.stereotype.Component;

import java.util.List;
import java.util.stream.Collectors;

@Component
@Slf4j
public class EntityMapper {
    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private EncryptionUtil encryptionUtil;

    public TransactionDTO toTransactionDTO(Transaction transaction) {
        if (transaction == null) return null;
        
        TransactionDTO dto = TransactionDTO.builder()
            .id(transaction.getId())
            .accountId(transaction.getAccount() != null ? transaction.getAccount().getUser().getId() : null)
            .categoryId(transaction.getCategory() != null ? transaction.getCategory().getId() : null)
            .amount(transaction.getAmount())
            .transactionType(transaction.getTransactionType())
            .source(transaction.getSource())
            .description(transaction.getDescription())
            .rawText(transaction.getRawText())
            .transactionDate(transaction.getTransactionDate())
            .availableLimitAtTransaction(transaction.getAvailableLimitAtTransaction())
            .createdAt(transaction.getCreatedAt())
            .updatedAt(transaction.getUpdatedAt())
            .accountName(transaction.getAccount() != null ? transaction.getAccount().getAccountName() : null)
            .categoryName(transaction.getCategory() != null ? transaction.getCategory().getName() : null)
            .deliveryMetadata(transaction.getDeliveryMetadata())
            .build();
        
        if (transaction.getDeliveryMetadata() != null && !transaction.getDeliveryMetadata().isEmpty()) {
            try {
                DeliveryMetadataDTO metadata = objectMapper.readValue(
                    transaction.getDeliveryMetadata(), DeliveryMetadataDTO.class);
                dto.setDeliveryCount(metadata.getDeliveries().size());
                dto.setTotalDeliveredItems(metadata.getTotalItemCount());
            } catch (Exception e) {
                log.warn("Failed to parse delivery metadata for transaction {}", transaction.getId());
            }
        }
        
        return dto;
    }
    
    public List<TransactionDTO> toTransactionDTOList(List<Transaction> transactions) {
        if (transactions == null) {
            return null;
        }
        return transactions.stream()
            .map(this::toTransactionDTO)
            .collect(Collectors.toList());
    }
    
    public AccountDTO toAccountDTO(Account account) {
        if (account == null) {
            return null;
        }

        String decryptedAccountNumber = null;
        try {
            decryptedAccountNumber = encryptionUtil.decryptIfPresent(account.getAccountNumber());
        } catch (Exception e) {
            log.warn("Could not decrypt account number for account {}: {}", account.getId(), e.getMessage());
        }

        return AccountDTO.builder()
            .id(account.getId())
            .userId(account.getUser() != null ? account.getUser().getId() : null)
            .accountName(account.getAccountName())
            .accountNumber(decryptedAccountNumber)
            .bankName(account.getBankName())
            .accountType(account.getAccountType())
            .currentBalance(account.getCurrentBalance())
            .isActive(account.getIsActive())
            .createdAt(account.getCreatedAt())
            .updatedAt(account.getUpdatedAt())
            .build();
    }
    
    public List<AccountDTO> toAccountDTOList(List<Account> accounts) {
        if (accounts == null) {
            return null;
        }
        return accounts.stream()
            .map(this::toAccountDTO)
            .collect(Collectors.toList());
    }
    
    public CategoryDTO toCategoryDTO(Category category) {
        if (category == null) {
            return null;
        }
        
        return CategoryDTO.builder()
            .id(category.getId())
            .name(category.getName())
            .description(category.getDescription())
            .icon(category.getIcon())
            .color(category.getColor())
            .createdAt(category.getCreatedAt())
            .updatedAt(category.getUpdatedAt())
            .build();
    }
    
    public List<CategoryDTO> toCategoryDTOList(List<Category> categories) {
        if (categories == null) {
            return null;
        }
        return categories.stream()
            .map(this::toCategoryDTO)
            .collect(Collectors.toList());
    }
    
    public UserDTO toUserDTO(User user) {
        if (user == null) {
            return null;
        }
        
        return UserDTO.builder()
            .id(user.getId())
            .username(user.getUsername())
            .email(user.getEmail())
            .createdAt(user.getCreatedAt())
            .updatedAt(user.getUpdatedAt())
            .build();
    }
    
    public List<UserDTO> toUserDTOList(List<User> users) {
        if (users == null) {
            return null;
        }
        return users.stream()
            .map(this::toUserDTO)
            .collect(Collectors.toList());
    }

}
