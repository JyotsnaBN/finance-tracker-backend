package com.financetracker.repository;

import com.financetracker.model.Transaction;
import com.financetracker.model.TransactionType;
import com.financetracker.model.TransactionSource;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

@Repository
public interface TransactionRepository extends JpaRepository<Transaction, Long> {
    List<Transaction> findByAccountUserId(Long userId);
    List<Transaction> findByAccountUserIdAndCategoryId(Long userId, Long categoryId);
    List<Transaction> findByAccountUserIdAndTransactionType(Long userId, TransactionType type);

    Page<Transaction> findByAccountUserIdAndTransactionDateBetween(
        Long userId, Instant start, Instant end, Pageable pageable);

    Page<Transaction> findByAccountUserIdAndTransactionTypeAndTransactionDateBetween(
        Long userId, TransactionType type, Instant start, Instant end, Pageable pageable);

    Page<Transaction> findByAccountUserIdAndCategoryIdAndTransactionDateBetween(
        Long userId, Long categoryId, Instant start, Instant end, Pageable pageable);

    Page<Transaction> findByAccountIdAndTransactionDateBetween(
        Long accountId, Instant start, Instant end, Pageable pageable);

    List<Transaction> findBySource(TransactionSource source);
    List<Transaction> findByAccountUserIdAndSource(Long userId, TransactionSource source);

    @Query("SELECT SUM(t.amount) FROM Transaction t WHERE t.account.user.id = :userId AND t.transactionType = :type AND t.transactionDate BETWEEN :start AND :end")
    BigDecimal getTotalByUserAndTypeAndDateRange(
        @Param("userId") Long userId,
        @Param("type") TransactionType type, 
        @Param("start") Instant start, 
        @Param("end") Instant end);

    @Query("SELECT t.category.name, SUM(t.amount) FROM Transaction t WHERE t.account.user.id = :userId AND t.transactionType = 'DEBIT' AND t.transactionDate BETWEEN :start AND :end GROUP BY t.category.name")
    List<Object[]> getCategoryWiseExpensesByUser(
        @Param("userId") Long userId,
        @Param("start") Instant start, 
        @Param("end") Instant end);
}