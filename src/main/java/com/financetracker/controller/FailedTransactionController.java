package com.financetracker.controller;

import com.financetracker.dto.FailedTransactionDTO;
import com.financetracker.model.FailedTransaction;
import com.financetracker.repository.FailedTransactionRepository;
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
    
    @GetMapping
    public ResponseEntity<List<FailedTransactionDTO>> getFailedTransactions(
            @RequestParam UUID userId,
            @RequestParam(required = false) Boolean resolved) {
        
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
        FailedTransaction failed = failedTransactionRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Failed transaction not found"));
        
        return ResponseEntity.ok(toDTO(failed));
    }
    
    @PostMapping("/{id}/resolve")
    public ResponseEntity<Void> resolveFailedTransaction(
            @PathVariable Long id,
            @RequestParam Long transactionId) {
        
        log.info("Resolving failed transaction: {} with transaction: {}", id, transactionId);
        
        FailedTransaction failed = failedTransactionRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Failed transaction not found"));
        
        failed.setResolved(true);
        failed.setResolvedTransactionId(transactionId);
        failedTransactionRepository.save(failed);
        
        log.info("Failed transaction {} resolved successfully", id);
        return ResponseEntity.ok().build();
    }
    
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteFailedTransaction(@PathVariable Long id) {
        log.info("Deleting failed transaction: {}", id);
        
        if (!failedTransactionRepository.existsById(id)) {
            throw new RuntimeException("Failed transaction not found");
        }
        
        failedTransactionRepository.deleteById(id);
        log.info("Failed transaction {} deleted successfully", id);
        return ResponseEntity.ok().build();
    }
    
    @GetMapping("/stats")
    public ResponseEntity<FailedTransactionStats> getStats(@RequestParam UUID userId) {
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
