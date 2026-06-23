package com.financetracker.dto;

import com.financetracker.model.TransactionSource;
import com.financetracker.model.TransactionType;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TransactionDTO {
    private Long id;
    
    @NotNull(message = "Account ID is required")
    private UUID accountId;
    
    @NotNull(message = "Category ID is required")
    private Long categoryId;
    
    @NotNull(message = "Amount is required")
    @DecimalMin(value = "0.01", message = "Amount must be greater than 0")
    private BigDecimal amount;
    
    @NotNull(message = "Transaction type is required")
    private TransactionType transactionType;
    
    @NotNull(message = "Transaction source is required")
    private TransactionSource source;
    
    @Size(max = 500, message = "Description cannot exceed 500 characters")
    private String description;
    
    private String rawText;
    
    @NotNull(message = "Transaction date is required")
    private Instant transactionDate;

    private BigDecimal availableLimitAtTransaction;
    
    private Instant createdAt;
    private Instant updatedAt;
    
    private String accountName;
    private String categoryName;

    private String deliveryMetadata;
    private Integer deliveryCount;
    private Integer totalDeliveredItems;

}
