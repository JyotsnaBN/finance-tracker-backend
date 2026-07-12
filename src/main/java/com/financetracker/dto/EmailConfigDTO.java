package com.financetracker.dto;

import lombok.*;
import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EmailConfigDTO {
    private Long id;
    private String emailAddress;
    private Boolean isActive;
    private Instant lastSync;
    private Instant tokenExpiry;
    private Boolean tokenExpired;
}
