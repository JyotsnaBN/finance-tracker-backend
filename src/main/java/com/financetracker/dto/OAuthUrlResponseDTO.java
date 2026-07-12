package com.financetracker.dto;

import lombok.*;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OAuthUrlResponseDTO {
    private String authorizationUrl;
    private String message;
}
