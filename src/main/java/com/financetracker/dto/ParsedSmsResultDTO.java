package com.financetracker.dto;

import com.financetracker.model.TransactionSource;
import com.financetracker.model.TransactionType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ParsedSmsResultDTO {
    
    private BigDecimal amount;
    private TransactionType transactionType;
    private Instant transactionDate;
    private String description;
    private String rawText;
    private TransactionSource source;
    private String accountHint;
    private boolean requiresManualReview;
    private String parserNotes;
    private BigDecimal availableLimit;
}
