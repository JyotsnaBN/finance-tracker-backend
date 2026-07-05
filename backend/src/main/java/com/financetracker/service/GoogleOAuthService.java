package com.financetracker.service;

import com.financetracker.config.GoogleOAuthConfig;
import com.financetracker.exception.OAuthException;
import com.financetracker.exception.EmailConfigurationException;
import com.financetracker.model.User;
import com.financetracker.model.UserEmailConfig;
import com.financetracker.repository.UserEmailConfigRepository;
import com.financetracker.util.EncryptionUtil;
import com.google.api.client.googleapis.auth.oauth2.*;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Collections;
import java.util.UUID;

@Service
@Slf4j
public class GoogleOAuthService {
    
    @Autowired
    private GoogleOAuthConfig oauthConfig;
    
    @Autowired
    private UserEmailConfigRepository emailConfigRepository;
    
    @Autowired
    private EncryptionUtil encryptionUtil;
    
    private static final NetHttpTransport HTTP_TRANSPORT = new NetHttpTransport();
    private static final GsonFactory JSON_FACTORY = GsonFactory.getDefaultInstance();
    
    public String generateAuthorizationUrl(String stateToken) {
        try {
            GoogleAuthorizationCodeFlow flow = createFlow();
            
            return flow.newAuthorizationUrl()
                    .setRedirectUri(oauthConfig.getRedirectUri())
                    .setState(stateToken)
                    .build();
        } catch (Exception e) {
            log.error("Failed to generate OAuth authorization URL: {}", e.getMessage(), e);
            throw new OAuthException("Failed to initiate email connection", e);
        }
    }
    
    @Transactional
    public UserEmailConfig exchangeCodeForTokens(String code, User user) {
        try {
            GoogleAuthorizationCodeFlow flow = createFlow();
            
            GoogleTokenResponse tokenResponse = flow.newTokenRequest(code)
                    .setRedirectUri(oauthConfig.getRedirectUri())
                    .execute();
            
            String emailAddress = getUserEmail(tokenResponse.getAccessToken());
            
            UserEmailConfig config = emailConfigRepository
                    .findByUserIdAndEmailAddress(user.getId(), emailAddress)
                    .orElse(new UserEmailConfig());
            
            config.setUser(user);
            config.setEmailAddress(emailAddress);
            config.setEncryptedAccessToken(encryptionUtil.encrypt(tokenResponse.getAccessToken()));
            config.setEncryptedRefreshToken(encryptionUtil.encrypt(tokenResponse.getRefreshToken()));
            config.setTokenExpiry(Instant.now().plusSeconds(tokenResponse.getExpiresInSeconds()));
            config.setIsActive(true);
            
            UserEmailConfig saved = emailConfigRepository.save(config);
            log.info("Successfully exchanged OAuth code for tokens for user {} and email {}", user.getId(), emailAddress);
            return saved;
            
        } catch (Exception e) {
            log.error("Failed to exchange OAuth code for tokens for user {}: {}", user.getId(), e.getMessage(), e);
            throw new OAuthException("Failed to complete email connection", e);
        }
    }
    
    public String refreshAccessToken(UserEmailConfig config) {
        try {
            String refreshToken = encryptionUtil.decrypt(config.getEncryptedRefreshToken());
            
            GoogleRefreshTokenRequest request = new GoogleRefreshTokenRequest(
                    HTTP_TRANSPORT,
                    JSON_FACTORY,
                    refreshToken,
                    oauthConfig.getClientId(),
                    oauthConfig.getClientSecret()
            );
            
            GoogleTokenResponse response = request.execute();
            
            config.setEncryptedAccessToken(encryptionUtil.encrypt(response.getAccessToken()));
            config.setTokenExpiry(Instant.now().plusSeconds(response.getExpiresInSeconds()));
            emailConfigRepository.save(config);
            
            log.info("Successfully refreshed access token for email config {}", config.getId());
            return response.getAccessToken();
            
        } catch (Exception e) {
            log.error("Failed to refresh access token for email config {}: {}", config.getId(), e.getMessage(), e);
            throw new OAuthException("Failed to refresh email access token", e);
        }
    }
    
    private GoogleAuthorizationCodeFlow createFlow() {
        return new GoogleAuthorizationCodeFlow.Builder(
                HTTP_TRANSPORT,
                JSON_FACTORY,
                oauthConfig.getClientId(),
                oauthConfig.getClientSecret(),
                Arrays.asList(oauthConfig.getScopes().split("\\s+")) 
        )
        .setAccessType("offline")
        .setApprovalPrompt("force")
        .build();
    }
    
    private String getUserEmail(String accessToken) throws Exception {
        try {
            String url = "https://www.googleapis.com/oauth2/v2/userinfo";
            com.google.api.client.http.HttpRequest request = HTTP_TRANSPORT.createRequestFactory()
                    .buildGetRequest(new com.google.api.client.http.GenericUrl(url));
            request.getHeaders().setAuthorization("Bearer " + accessToken);
            
            com.google.api.client.http.HttpResponse response = request.execute();
            String content = response.parseAsString();
            
            com.google.api.client.json.JsonParser parser = JSON_FACTORY.createJsonParser(content);
            @SuppressWarnings("unchecked")
            java.util.Map<String, Object> userInfo = parser.parse(java.util.Map.class);
            
            String email = (String) userInfo.get("email");
            if (email == null || email.isEmpty()) {
                throw new EmailConfigurationException("Unable to retrieve email address from Google");
            }
            
            log.debug("Successfully retrieved email address from Google OAuth");
            return email;
            
        } catch (Exception e) {
            log.error("Failed to retrieve user email from Google: {}", e.getMessage(), e);
            throw new EmailConfigurationException("Failed to retrieve email address from Google", e);
        }
    }
}
