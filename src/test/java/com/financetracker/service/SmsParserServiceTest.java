package com.financetracker.service;

import com.financetracker.dto.ParsedSmsResultDTO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class SmsParserServiceTest {
    
    private SmsParserService smsParserService;
    
    @BeforeEach
    void setUp() {
        smsParserService = new SmsParserService();
    }
    
    @Test
    void testDebitTransaction() {
        String sms = "Rs.500 debited from A/c XX1234 at SWIGGY on 15-Jan-2024";
        
        ParsedSmsResultDTO result = smsParserService.parseSms(sms);
        
        System.out.println("=== PARSED RESULT ===");
        System.out.println("Amount: " + result.getAmount());
        System.out.println("Type: " + result.getTransactionType());
        System.out.println("Date: " + result.getTransactionDate());
        System.out.println("Account Hint: " + result.getAccountHint());
        System.out.println("Description: " + result.getDescription());
        System.out.println("Requires Review: " + result.isRequiresManualReview());
        System.out.println("Parser Notes: " + result.getParserNotes());
    }
}
