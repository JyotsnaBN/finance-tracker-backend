package com.financetracker.controller;

import com.financetracker.dto.FailedTransactionDTO;
import com.financetracker.dto.TransactionDTO;
import com.financetracker.model.FailedTransaction;
import com.financetracker.model.TransactionSource;
import com.financetracker.repository.FailedTransactionRepository;
import com.financetracker.security.SecurityUtils;
import com.financetracker.service.TransactionService;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/failed-transactions")
@Slf4j
public class FailedTransactionController {

    @Autowired
    private FailedTransactionRepository failedTransactionRepository;

    @Autowired
    private TransactionService transactionService;

    @GetMapping
    public ResponseEntity<List<FailedTransactionDTO>> getFailedTransactions(
            @RequestParam(required = false) Boolean resolved) {
        UUID userId = SecurityUtils.getAuthenticatedUserId();
        log.info("Fetching failed transactions for user: {}", userId);

        List<FailedTransaction> failed;
        if (resolved != null) {
            failed = failedTransactionRepository.findByUserIdAndResolved(userId, resolved);
        } else {
            failed = failedTransactionRepository.findByUserId(userId);
        }

        List<FailedTransactionDTO> dtos = failed.stream()
                .map(this::toDTO)
                .collect(Collectors.toList());

        return ResponseEntity.ok(dtos);
    }

    @GetMapping("/{id}")
    public ResponseEntity<FailedTransactionDTO> getFailedTransaction(@PathVariable Long id) {
        log.debug("Fetching failed transaction with id: {}", id);
        UUID authenticatedUserId = SecurityUtils.getAuthenticatedUserId();
        FailedTransaction failed = failedTransactionRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Failed transaction not found"));
        if (!authenticatedUserId.equals(failed.getUser().getId())) {
            return ResponseEntity.status(org.springframework.http.HttpStatus.FORBIDDEN).build();
        }
        return ResponseEntity.ok(toDTO(failed));
    }

    @PostMapping("/{id}/resolve")
    public ResponseEntity<Void> resolveFailedTransaction(
            @PathVariable Long id,
            @RequestParam(required = false) UUID accountId) {
        log.info("Resolving failed transaction: {}, accountId={}", id, accountId);
        UUID authenticatedUserId = SecurityUtils.getAuthenticatedUserId();

        FailedTransaction failed = failedTransactionRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Failed transaction not found"));
        if (!authenticatedUserId.equals(failed.getUser().getId())) {
            return ResponseEntity.status(org.springframework.http.HttpStatus.FORBIDDEN).build();
        }

        // Cannot auto-resolve without an accountId — return 422 so future UI can handle it
        if (accountId == null) {
            log.warn("Cannot auto-resolve failed transaction {} — accountId not provided", id);
            return ResponseEntity.unprocessableEntity().build();
        }

        // Build a TransactionDTO from the stored parsed data and create the transaction
        TransactionDTO dto = TransactionDTO.builder()
                .accountId(accountId)
                .amount(failed.getAmount())
                .transactionType(failed.getTransactionType())
                .transactionDate(failed.getTransactionDate())
                .description(failed.getDescription())
                .source(TransactionSource.MANUAL) // resolved manually
                .build();

        try {
            TransactionDTO created = transactionService.createTransaction(dto);
            failed.setResolved(true);
            failed.setResolvedTransactionId(created.getId());
            failedTransactionRepository.save(failed);
            log.info("Failed transaction {} resolved → transaction {}", id, created.getId());
            return ResponseEntity.ok().build();
        } catch (Exception e) {
            log.error("Failed to auto-resolve transaction {}: {}", id, e.getMessage());
            return ResponseEntity.status(org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteFailedTransaction(@PathVariable Long id) {
        log.info("Deleting failed transaction: {}", id);
        UUID authenticatedUserId = SecurityUtils.getAuthenticatedUserId();

        FailedTransaction failed = failedTransactionRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Failed transaction not found"));
        if (!authenticatedUserId.equals(failed.getUser().getId())) {
            return ResponseEntity.status(org.springframework.http.HttpStatus.FORBIDDEN).build();
        }

        failedTransactionRepository.deleteById(id);
        log.info("Failed transaction {} deleted successfully", id);
        return ResponseEntity.ok().build();
    }

    @GetMapping("/stats")
    public ResponseEntity<FailedTransactionStats> getStats() {
        UUID userId = SecurityUtils.getAuthenticatedUserId();
        long total = failedTransactionRepository.countByUserId(userId);
        long unresolved = failedTransactionRepository.countByUserIdAndResolved(userId, false);

        return ResponseEntity.ok(new FailedTransactionStats(total, unresolved));
    }

    private FailedTransactionDTO toDTO(FailedTransaction failed) {
        return FailedTransactionDTO.builder()
                .id(failed.getId())
                .emailAccount(failed.getEmailAccount())
                .rawEmailContent(failed.getRawEmailContent())
                .failureReason(failed.getFailureReason())
                .accountHint(failed.getAccountHint())
                .amount(failed.getAmount())
                .transactionType(failed.getTransactionType())
                .transactionDate(failed.getTransactionDate())
                .description(failed.getDescription())
                .requiresManualReview(failed.getRequiresManualReview())
                .resolved(failed.getResolved())
                .createdAt(failed.getCreatedAt())
                .build();
    }

    @Data
    @AllArgsConstructor
    static class FailedTransactionStats {
        private long total;
        private long unresolved;
    }
}
