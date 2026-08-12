package com.financetracker.dto;

import com.financetracker.model.TransactionSource;
import com.financetracker.model.TransactionType;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;
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

    private UUID accountId;
    /** The owner user's UUID — set server-side, never trusted from client. */
    private UUID userId;
    private Long categoryId;

    @NotNull(message = "Amount is required")
    @DecimalMin(value = "0.01", message = "Amount must be greater than 0")
    @DecimalMax(value = "999999999.99", message = "Amount exceeds maximum allowed value")
    @Digits(integer = 9, fraction = 2, message = "Amount must have at most 9 integer digits and 2 decimal places")
    private BigDecimal amount;

    @NotNull(message = "Transaction type is required")
    private TransactionType transactionType;

    @NotNull(message = "Transaction source is required")
    private TransactionSource source;

    @Size(max = 500, message = "Description cannot exceed 500 characters")
    private String description;

    /** Raw SMS/email text — server-populated, ignored on inbound requests. */
    private String rawText;

    @NotNull(message = "Transaction date is required")
    @PastOrPresent(message = "Transaction date cannot be in the future")
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
