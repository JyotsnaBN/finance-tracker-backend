package com.financetracker.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class SmsIngestRequestDTO {

    @NotBlank(message = "smsBody is required")
    private String smsBody;

    /** Optional — the sender address/number extracted by the Android app. */
    private String sender;
}
