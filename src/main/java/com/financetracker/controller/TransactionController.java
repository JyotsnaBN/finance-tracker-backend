package com.financetracker.controller;

import com.financetracker.dto.TransactionDTO;
import com.financetracker.dto.BulkTransactionRequestDTO;
import com.financetracker.dto.BulkTransactionResponseDTO;
import com.financetracker.security.SecurityUtils;
import com.financetracker.service.TransactionService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/transactions")
@RequiredArgsConstructor
public class TransactionController {
    private final TransactionService transactionService;

    @GetMapping
    public ResponseEntity<List<TransactionDTO>> getAllTransactions() {
        return ResponseEntity.ok(transactionService.getTransactionsByUserId(
                SecurityUtils.getAuthenticatedUserId()));
    }

    @GetMapping("/{id}")
    public ResponseEntity<TransactionDTO> getTransactionById(@PathVariable Long id) {
        UUID authenticatedUserId = SecurityUtils.getAuthenticatedUserId();
        TransactionDTO transaction = transactionService.getTransactionById(id);
        // accountId in TransactionDTO is mapped from account.user.id (see EntityMapper)
        if (!authenticatedUserId.equals(transaction.getAccountId())) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        return ResponseEntity.ok(transaction);
    }

    @PostMapping
    public ResponseEntity<TransactionDTO> createTransaction(@Valid @RequestBody TransactionDTO dto) {
        return new ResponseEntity<>(transactionService.createTransaction(dto), HttpStatus.CREATED);
    }

    @PostMapping("/bulk")
    public ResponseEntity<BulkTransactionResponseDTO> createBulkTransactions(
            @Valid @RequestBody BulkTransactionRequestDTO request) {
        BulkTransactionResponseDTO response = transactionService.createBulkTransactions(request);
        return new ResponseEntity<>(response, HttpStatus.CREATED);
    }

    @PutMapping("/{id}")
    public ResponseEntity<TransactionDTO> updateTransaction(
            @PathVariable Long id,
            @Valid @RequestBody TransactionDTO dto) {
        UUID authenticatedUserId = SecurityUtils.getAuthenticatedUserId();
        TransactionDTO existing = transactionService.getTransactionById(id);
        if (!authenticatedUserId.equals(existing.getAccountId())) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        return ResponseEntity.ok(transactionService.updateTransaction(id, dto));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteTransaction(@PathVariable Long id) {
        UUID authenticatedUserId = SecurityUtils.getAuthenticatedUserId();
        TransactionDTO existing = transactionService.getTransactionById(id);
        if (!authenticatedUserId.equals(existing.getAccountId())) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        transactionService.deleteTransaction(id);
        return ResponseEntity.noContent().build();
    }
}
