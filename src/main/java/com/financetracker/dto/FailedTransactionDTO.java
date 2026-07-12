package com.financetracker.dto;

import com.financetracker.model.TransactionType;
import lombok.*;
import java.math.BigDecimal;
import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FailedTransactionDTO {
    private Long id;
    private String emailAccount;
    private String rawEmailContent;
    private String failureReason;
    private String accountHint;
    private BigDecimal amount;
    private TransactionType transactionType;
    private Instant transactionDate;
    private String description;
    private Boolean requiresManualReview;
    private Boolean resolved;
    private Instant createdAt;
}
