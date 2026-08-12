package com.financetracker.dto;

import com.financetracker.model.AccountType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
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
    @Size(min = 1, max = 100, message = "Account name must be between 1 and 100 characters")
    private String accountName;

    /** Last 4 digits or masked value stored encrypted — reject oversized input. */
    @Size(max = 20, message = "Account number cannot exceed 20 characters")
    @Pattern(regexp = "^[0-9Xx*-]*$",
             message = "Account number may only contain digits and masking characters")
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
