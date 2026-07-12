package com.financetracker.repository;

import com.financetracker.model.FailedTransaction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface FailedTransactionRepository extends JpaRepository<FailedTransaction, Long> {
    
    List<FailedTransaction> findByUserId(UUID userId);
    List<FailedTransaction> findByUserIdAndResolved(UUID userId, Boolean resolved);
    List<FailedTransaction> findByUserIdAndResolvedFalse(UUID userId);
    List<FailedTransaction> findByUserIdAndRequiresManualReviewTrue(UUID userId);
    List<FailedTransaction> findByEmailConfigId(Long emailConfigId);
    long countByUserId(UUID userId);
    long countByUserIdAndResolved(UUID userId, Boolean resolved);
    long countByUserIdAndResolvedFalse(UUID userId);
    long countByUserIdAndRequiresManualReviewTrue(UUID userId);
    
    @Query("SELECT ft.failureReason, COUNT(ft) FROM FailedTransaction ft " +
           "WHERE ft.user.id = :userId AND ft.resolved = false " +
           "GROUP BY ft.failureReason")
    List<Object[]> getFailureReasonStats(@Param("userId") UUID userId);

}
