package com.financetracker.controller;

import com.financetracker.dto.EmailConfigDTO;
import com.financetracker.dto.OAuthUrlResponseDTO;
import com.financetracker.model.User;
import com.financetracker.model.UserEmailConfig;
import com.financetracker.repository.UserEmailConfigRepository;
import com.financetracker.repository.UserRepository;
import com.financetracker.security.JwtTokenProvider;
import com.financetracker.service.GoogleOAuthService;
import com.financetracker.service.EmailReaderService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/email")
@Slf4j
public class EmailConfigController {
    
    @Autowired
    private GoogleOAuthService oauthService;
    
    @Autowired
    private UserRepository userRepository;
    
    @Autowired
    private UserEmailConfigRepository emailConfigRepository;
    
    @Autowired
    private JwtTokenProvider tokenProvider;

    @Autowired
    private EmailReaderService emailReaderService;

    private final java.util.concurrent.ConcurrentHashMap<String, String> oauthStateStore
        = new java.util.concurrent.ConcurrentHashMap<>();
        
    @PostMapping("/connect")
    public ResponseEntity<?> initiateConnection() {                     // removed HttpSession
        UUID authenticatedUserId = getAuthenticatedUserId();
        log.info("Initiating email connection for user: {}", authenticatedUserId);

        User user = userRepository.findById(authenticatedUserId)
                .orElseThrow(() -> new RuntimeException("User not found"));

        String stateToken = UUID.randomUUID().toString();
        oauthStateStore.put(stateToken, authenticatedUserId.toString());

        String authUrl = oauthService.generateAuthorizationUrl(stateToken);

        return ResponseEntity.ok(OAuthUrlResponseDTO.builder()
                .authorizationUrl(authUrl)
                .message("Please visit the URL to authorize Gmail access")
                .build());
    }
    
    @GetMapping("/oauth/callback")
    public ResponseEntity<String> handleOAuthCallback(
            @RequestParam String code,
            @RequestParam String state) {                               // removed HttpSession

        log.info("Handling OAuth callback with state token");

        String userIdStr = oauthStateStore.remove(state);
        if (userIdStr == null) {
            log.error("OAuth callback: No user ID found for state token");
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body("Invalid OAuth session. Please try again.");
        }

        UUID userId = UUID.fromString(userIdStr);

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));

        UserEmailConfig config = oauthService.exchangeCodeForTokens(code, user);

        log.info("Successfully connected email for user: {}", userId);

        return ResponseEntity.ok("Email account connected successfully! You can close this window.");
    }
    
    @GetMapping("/status")
    public ResponseEntity<?> getEmailConfigs() {
        UUID authenticatedUserId = getAuthenticatedUserId();
        log.info("Fetching email configs for user: {}", authenticatedUserId);
        
        List<UserEmailConfig> configs = emailConfigRepository.findByUserId(authenticatedUserId);
        
        List<EmailConfigDTO> dtos = configs.stream()
                .map(this::toDTO)
                .collect(Collectors.toList());
        
        return ResponseEntity.ok(dtos);
    }
    
    @DeleteMapping("/disconnect")
    public ResponseEntity<?> disconnectEmail(@RequestParam String emailAddress) {
        UUID authenticatedUserId = getAuthenticatedUserId();
        log.info("Disconnecting email: {} for user: {}", emailAddress, authenticatedUserId);
        
        UserEmailConfig config = emailConfigRepository
                .findByUserIdAndEmailAddress(authenticatedUserId, emailAddress)
                .orElseThrow(() -> new RuntimeException("Email configuration not found"));
        
        config.setIsActive(false);
        emailConfigRepository.save(config);
        
        log.info("Email {} disconnected successfully for user: {}", emailAddress, authenticatedUserId);
        return ResponseEntity.ok().build();
    }
    
    @PostMapping("/sync")
    public ResponseEntity<String> triggerManualSync() {
        UUID authenticatedUserId = getAuthenticatedUserId();
        log.info("Manual sync triggered by user: {}", authenticatedUserId);

        emailReaderService.processAllEmails();
        return ResponseEntity.ok("Sync completed");
    }
        
    /**
     * Extract authenticated user ID from JWT token in SecurityContext
     */
    private UUID getAuthenticatedUserId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new RuntimeException("User not authenticated");
        }
        
        Object principal = authentication.getPrincipal();
        if (principal instanceof UUID userId) {
            return userId;
        }
        return UUID.fromString(authentication.getName());
    }
    
    private EmailConfigDTO toDTO(UserEmailConfig config) {
        return EmailConfigDTO.builder()
                .id(config.getId())
                .emailAddress(config.getEmailAddress())
                .isActive(config.getIsActive())
                .lastSync(config.getLastSync())
                .tokenExpiry(config.getTokenExpiry())
                .tokenExpired(config.getTokenExpiry() != null && 
                             config.getTokenExpiry().isBefore(Instant.now()))
                .build();
    }
}
