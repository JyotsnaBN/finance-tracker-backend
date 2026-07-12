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
import java.util.UUID;

@Repository
public interface TransactionRepository extends JpaRepository<Transaction, Long> {
    List<Transaction> findByAccountUserId(UUID userId);
    List<Transaction> findByAccountUserIdAndCategoryId(UUID userId, Long categoryId);
    List<Transaction> findByAccountUserIdAndTransactionType(UUID userId, TransactionType type);

    Page<Transaction> findByAccountUserIdAndTransactionDateBetween(
        UUID userId, Instant start, Instant end, Pageable pageable);

    Page<Transaction> findByAccountUserIdAndTransactionTypeAndTransactionDateBetween(
        UUID userId, TransactionType type, Instant start, Instant end, Pageable pageable);

    Page<Transaction> findByAccountUserIdAndCategoryIdAndTransactionDateBetween(
        UUID userId, Long categoryId, Instant start, Instant end, Pageable pageable);

    Page<Transaction> findByAccountIdAndTransactionDateBetween(
        UUID accountId, Instant start, Instant end, Pageable pageable);

    List<Transaction> findBySource(TransactionSource source);
    List<Transaction> findByAccountUserIdAndSource(UUID userId, TransactionSource source);

    @Query("SELECT SUM(t.amount) FROM Transaction t WHERE t.account.user.id = :userId AND t.transactionType = :type AND t.transactionDate BETWEEN :start AND :end")
    BigDecimal getTotalByUserAndTypeAndDateRange(
        @Param("userId") UUID userId,
        @Param("type") TransactionType type, 
        @Param("start") Instant start, 
        @Param("end") Instant end);

    @Query("SELECT t.category.name, SUM(t.amount) FROM Transaction t WHERE t.account.user.id = :userId AND t.transactionType = 'DEBIT' AND t.transactionDate BETWEEN :start AND :end GROUP BY t.category.name")
    List<Object[]> getCategoryWiseExpensesByUser(
        @Param("userId") UUID userId,
        @Param("start") Instant start, 
        @Param("end") Instant end);

    @Query("SELECT CASE WHEN COUNT(t) > 0 THEN true ELSE false END " +
           "FROM Transaction t WHERE t.account.user.id = :userId " +
           "AND t.rawText = :rawText")
    boolean existsByAccountUserIdAndRawText(
        @Param("userId") UUID userId,
        @Param("rawText") String rawText
    );
    
    @Query("SELECT CASE WHEN COUNT(t) > 0 THEN true ELSE false END " +
           "FROM Transaction t WHERE t.account.id = :accountId " +
           "AND t.amount = :amount " +
           "AND t.transactionType = :transactionType " +
           "AND t.transactionDate = :transactionDate")
    boolean existsByAccountIdAndAmountAndTypeAndDate(
        @Param("accountId") UUID accountId,
        @Param("amount") BigDecimal amount,
        @Param("transactionType") TransactionType transactionType,
        @Param("transactionDate") Instant transactionDate
    );
    
    @Query("SELECT CASE WHEN COUNT(t) > 0 THEN true ELSE false END " +
           "FROM Transaction t WHERE t.account.id = :accountId " +
           "AND t.amount = :amount " +
           "AND t.transactionType = :transactionType " +
           "AND t.transactionDate BETWEEN :startDate AND :endDate")
    boolean existsSimilarTransaction(
        @Param("accountId") UUID accountId,
        @Param("amount") BigDecimal amount,
        @Param("transactionType") TransactionType transactionType,
        @Param("startDate") Instant startDate,
        @Param("endDate") Instant endDate
    );
    
    @Query("SELECT t FROM Transaction t WHERE t.account.user.id = :userId " +
        "AND t.amount = :amount " +
        "AND t.transactionDate BETWEEN :startDate AND :endDate " +
        "AND t.transactionType = com.financetracker.model.TransactionType.DEBIT " +
        "ORDER BY t.transactionDate")
    List<Transaction> findByUserIdAndAmountAndDateRangeOrderByClosestToTarget(
        @Param("userId") UUID userId,
        @Param("amount") BigDecimal amount,
        @Param("startDate") Instant startDate,
        @Param("endDate") Instant endDate,
        @Param("targetDate") Instant targetDate
    );

    @Query("SELECT CASE WHEN COUNT(t) > 0 THEN true ELSE false END " +
        "FROM Transaction t WHERE t.deliveryMetadata LIKE %:trackingNumber%")
    boolean existsByDeliveryTrackingNumber(@Param("trackingNumber") String trackingNumber);

}