package com.financetracker.repository;

import com.financetracker.model.Account;
import com.financetracker.model.AccountType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface AccountRepository extends JpaRepository<Account, Long> {
    List<Account> findByUserId(Long userId);
    List<Account> findByUserIdAndIsActive(Long userId, Boolean isActive);
    Optional<Account> findByIdAndUserId(Long id, Long userId);

    List<Account> findByUserIdAndAccountType(Long userId, AccountType accountType);
    
    Optional<Account> findByAccountNumber(String accountNumber);
    Optional<Account> findByAccountNumberAndUserId(String accountNumber, Long userId);
    
    boolean existsByAccountNumber(String accountNumber);
    boolean existsByIdAndUserId(Long id, Long userId);
    
    @Query("SELECT COUNT(a) FROM Account a WHERE a.user.id = :userId")
    long countByUserId(@Param("userId") Long userId);
    
    List<Account> findByUserIdAndIsActiveOrderByAccountNameAsc(Long userId, Boolean isActive);
}
