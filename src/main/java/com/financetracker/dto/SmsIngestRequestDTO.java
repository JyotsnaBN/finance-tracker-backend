package com.financetracker.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class SmsIngestRequestDTO {

    @NotBlank(message = "smsBody is required")
    @Size(max = 1600, message = "smsBody cannot exceed 1600 characters")
    private String smsBody;

    /**
     * Optional — the sender address or number extracted by the Android app.
     * Must be a phone number (E.164 or local) or a short alphanumeric sender ID.
     */
    @Size(max = 20, message = "sender cannot exceed 20 characters")
    @Pattern(regexp = "^[+0-9a-zA-Z ]*$",
             message = "sender contains invalid characters")
    private String sender;
}
