package com.financetracker.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BulkTransactionResponseDTO {
    private int created;
    private int failed;
    private List<TransactionDTO> transactions;
    private List<String> errors;
}
