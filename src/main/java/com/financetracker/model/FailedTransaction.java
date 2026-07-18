package com.financetracker.model;

import java.math.BigDecimal;
import java.time.Instant;

import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "failed_transactions")
@Getter @Setter @Builder
@NoArgsConstructor @AllArgsConstructor
public class FailedTransaction {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;
    
    @Column(name = "email_config_id")
    private Long emailConfigId;
    
    @Column(name = "email_account", nullable = true)
    private String emailAccount;
    
    @Column(name = "raw_email_content", columnDefinition = "TEXT")
    private String rawEmailContent;
    
    @Column(name = "failure_reason", nullable = false)
    private String failureReason;
    
    @Column(name = "account_hint")
    private String accountHint;
    
    @Column(name = "amount", precision = 15, scale = 2)
    private BigDecimal amount;
    
    @Enumerated(EnumType.STRING)
    @Column(name = "transaction_type")
    private TransactionType transactionType;
    
    @Column(name = "transaction_date")
    private Instant transactionDate;
    
    @Column(name = "description")
    private String description;
    
    @Column(name = "parsed_data_json", columnDefinition = "TEXT")
    private String parsedDataJson;
    
    @Column(name = "requires_manual_review", nullable = false)
    private Boolean requiresManualReview = true;
    
    @Column(name = "resolved", nullable = false)
    private Boolean resolved = false;
    
    @Column(name = "resolved_transaction_id")
    private Long resolvedTransactionId;
    
    @Column(name = "created_at", nullable = false, updatable = false)
    @CreationTimestamp
    private Instant createdAt;
    
    @Column(name = "updated_at", nullable = false)
    @UpdateTimestamp
    private Instant updatedAt;
}
