package com.financetracker.controller;

import com.financetracker.dto.UserDTO;
import com.financetracker.model.User;
import com.financetracker.repository.UserRepository;
import com.financetracker.security.JwtTokenProvider;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.util.Optional;

@RestController
@RequestMapping("/api/auth")
@Slf4j
public class AuthController {
    
    @Autowired
    private UserRepository userRepository;
    
    @Autowired
    private PasswordEncoder passwordEncoder;
    
    @Autowired
    private JwtTokenProvider tokenProvider;
    
    @PostMapping("/register")
    public ResponseEntity<AuthResponse> register(@RequestBody RegisterRequest request) {
        if (userRepository.findByEmail(request.getEmail()).isPresent()) {
            return ResponseEntity.badRequest().body(
                new AuthResponse(null, null, "Email already registered")
            );
        }
        
        User user = User.builder()
                .username(request.getUsername())
                .email(request.getEmail())
                .passwordHash(passwordEncoder.encode(request.getPassword()))
                .build();
        
        User savedUser = userRepository.save(user);
        String token = tokenProvider.generateToken(savedUser.getId(), savedUser.getEmail());
        
        log.info("User registered successfully: {}", savedUser.getEmail());
        return ResponseEntity.ok(new AuthResponse(token, savedUser.getId().toString(), "Registration successful"));
    }
    
    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@RequestBody LoginRequest request) {
        Optional<User> userOpt = userRepository.findByEmail(request.getEmail());
        
        if (userOpt.isEmpty()) {
            return ResponseEntity.badRequest().body(
                new AuthResponse(null, null, "Invalid credentials")
            );
        }
        
        User user = userOpt.get();
        if (!passwordEncoder.matches(request.getPassword(), user.getPasswordHash())) {
            return ResponseEntity.badRequest().body(
                new AuthResponse(null, null, "Invalid credentials")
            );
        }
        
        String token = tokenProvider.generateToken(user.getId(), user.getEmail());
        
        log.info("User logged in successfully: {}", user.getEmail());
        return ResponseEntity.ok(new AuthResponse(token, user.getId().toString(), "Login successful"));
    }
    
    @Data
    @AllArgsConstructor
    static class RegisterRequest {
        private String username;
        private String email;
        private String password;
    }
    
    @Data
    @AllArgsConstructor
    static class LoginRequest {
        private String email;
        private String password;
    }
    
    @Data
    @AllArgsConstructor
    static class AuthResponse {
        private String token;
        private String userId;
        private String message;
    }
}
