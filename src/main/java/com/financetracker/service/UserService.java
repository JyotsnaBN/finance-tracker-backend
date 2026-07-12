package com.financetracker.service;

import com.financetracker.dto.UserDTO;
import com.financetracker.exception.DuplicateResourceException;
import com.financetracker.model.User;
import com.financetracker.repository.UserRepository;
import com.financetracker.util.EntityMapper;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class UserService {
    private final UserRepository userRepository;
    private final EntityMapper entityMapper;
    private final PasswordEncoder passwordEncoder;
    
    @Transactional(readOnly = true)
    public UserDTO getUserById(UUID id) {
        log.debug("Fetching user with id: {}", id);
        User user = userRepository.findById(id)
            .orElseThrow(() -> new RuntimeException("User not found"));
        return entityMapper.toUserDTO(user);
    }
    
    @Transactional(readOnly = true)
    public UserDTO getUserByEmail(String email) {
        log.debug("Fetching user with email: {}", email);
        User user = userRepository.findByEmail(email)
            .orElseThrow(() -> new RuntimeException("User not found"));
        return entityMapper.toUserDTO(user);
    }
    
    @Transactional
    public UserDTO createUser(UserDTO dto) {
        log.info("Creating user: {}", dto.getUsername());
        
        if (userRepository.existsByEmail(dto.getEmail())) {
            throw new DuplicateResourceException("A user with this email already exists");
        }
        
        try {
            User user = User.builder()
                .username(dto.getUsername())
                .email(dto.getEmail())
                .passwordHash(passwordEncoder.encode(dto.getPassword()))
                .build();
            
            User saved = userRepository.save(user);
            log.info("User created successfully with id: {}", saved.getId());
            
            return entityMapper.toUserDTO(saved);
        } catch (Exception e) {
            log.error("Failed to create user {}: {}", dto.getUsername(), e.getMessage(), e);
            throw new RuntimeException("Failed to create user", e);
        }
    }
    
    @Transactional
    public UserDTO updateUser(UUID id, UserDTO dto) {
        log.info("Updating user with id: {}", id);
        
        User existing = userRepository.findById(id)
            .orElseThrow(() -> new RuntimeException("User not found"));
        
        if (!existing.getEmail().equals(dto.getEmail()) &&
            userRepository.existsByEmail(dto.getEmail())) {
            throw new DuplicateResourceException("A user with this email already exists");
        }
        
        try {
            existing.setUsername(dto.getUsername());
            existing.setEmail(dto.getEmail());
            
            User updated = userRepository.save(existing);
            log.info("User updated successfully: {}", id);
            
            return entityMapper.toUserDTO(updated);
        } catch (Exception e) {
            log.error("Failed to update user {}: {}", id, e.getMessage(), e);
            throw new RuntimeException("Failed to update user", e);
        }
    }
    
    @Transactional
    public void deleteUser(UUID id) {
        log.info("Deleting user with id: {}", id);
        
        if (!userRepository.existsById(id)) {
            throw new RuntimeException("User not found");
        }
        
        try {
            userRepository.deleteById(id);
            log.info("User deleted successfully: {}", id);
        } catch (Exception e) {
            log.error("Failed to delete user {}: {}", id, e.getMessage(), e);
            throw new RuntimeException("Failed to delete user. The user may have associated data.", e);
        }
    }
}
