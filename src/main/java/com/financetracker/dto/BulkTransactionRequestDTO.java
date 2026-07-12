package com.financetracker.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BulkTransactionRequestDTO {
    @NotEmpty(message = "Transactions list cannot be empty")
    @Valid
    private List<TransactionDTO> transactions;
}
