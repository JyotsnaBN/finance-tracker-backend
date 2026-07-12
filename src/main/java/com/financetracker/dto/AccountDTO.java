package com.financetracker.dto;

import com.financetracker.model.AccountType;
import jakarta.validation.constraints.NotBlank;
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
public class AccountDTO {
    private UUID id;
    
    private UUID userId;
    
    @NotBlank(message = "Account name is required")
    @Size(max = 100, message = "Account name cannot exceed 100 characters")
    private String accountName;
    
    @Size(max = 50, message = "Account number cannot exceed 50 characters")
    private String accountNumber;
    
    @Size(max = 100, message = "Bank name cannot exceed 100 characters")
    private String bankName;
    
    @NotNull(message = "Account type is required")
    private AccountType accountType;
    
    private BigDecimal currentBalance;
    
    private Boolean isActive;
    
    private Instant createdAt;
    private Instant updatedAt;
}
