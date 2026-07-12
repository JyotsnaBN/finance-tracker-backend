package com.financetracker.repository;

import com.financetracker.model.UserEmailConfig;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface UserEmailConfigRepository extends JpaRepository<UserEmailConfig, Long> {

    List<UserEmailConfig> findByUserId(UUID userId);
    Optional<UserEmailConfig> findByUserIdAndEmailAddress(UUID userId, String emailAddress);
    List<UserEmailConfig> findByIsActiveTrue();
    List<UserEmailConfig> findByUserIdAndIsActiveTrue(UUID userId);
    boolean existsByUserIdAndEmailAddress(UUID userId, String emailAddress);
    long countByUserId(UUID userId);
    
}
