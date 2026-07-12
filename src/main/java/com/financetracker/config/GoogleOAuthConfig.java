package com.financetracker.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import lombok.Getter;

@Configuration
@Getter
public class GoogleOAuthConfig {
    
    @Value("${google.oauth.client-id}")
    private String clientId;
    
    @Value("${google.oauth.client-secret}")
    private String clientSecret;
    
    @Value("${google.oauth.redirect-uri}")
    private String redirectUri;
    
    @Value("${google.oauth.scopes}")
    private String scopes;
}
