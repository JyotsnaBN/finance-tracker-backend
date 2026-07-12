package com.financetracker.controller;

import com.financetracker.dto.TransactionDTO;
import com.financetracker.service.TransactionService;
import com.financetracker.dto.BulkTransactionRequestDTO;
import com.financetracker.dto.BulkTransactionResponseDTO;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
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
        // Get transactions for authenticated user only
        UUID authenticatedUserId = getAuthenticatedUserId();
        return ResponseEntity.ok(transactionService.getTransactionsByUserId(authenticatedUserId));
    }
    
    @GetMapping("/user/{userId}")
    public ResponseEntity<List<TransactionDTO>> getTransactionsByUserId(@PathVariable UUID userId) {
        // Verify authenticated user matches requested userId
        UUID authenticatedUserId = getAuthenticatedUserId();
        if (!authenticatedUserId.equals(userId)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        return ResponseEntity.ok(transactionService.getTransactionsByUserId(userId));
    }

    @GetMapping("/{id}")
    public ResponseEntity<TransactionDTO> getTransactionById(@PathVariable Long id) {
        return ResponseEntity.ok(transactionService.getTransactionById(id));
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
        return ResponseEntity.ok(transactionService.updateTransaction(id, dto));
    }
    
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteTransaction(@PathVariable Long id) {
        transactionService.deleteTransaction(id);
        return ResponseEntity.noContent().build();
    }
    
    /**
     * Extract authenticated user ID from JWT token in SecurityContext
     */
    private UUID getAuthenticatedUserId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new RuntimeException("User not authenticated");
        }
        
        String userIdStr = authentication.getName();
        return UUID.fromString(userIdStr);
    }
}
