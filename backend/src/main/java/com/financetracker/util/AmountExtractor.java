package com.financetracker.util;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
@Slf4j
public class AmountExtractor {
    
    private static final List<Pattern> AMOUNT_PATTERNS = List.of(
        Pattern.compile(
            "(?:paid via [a-z]+|grand total|total paid|total amount|amount paid|bill amount|order total|order amount|net amount|total|amount).{0,20}?(?:INR|Rs\\.?|₹)\\s*([0-9,]+(?:\\.[0-9]{2})?)",
            Pattern.CASE_INSENSITIVE),
        
        Pattern.compile(
            "(?:paid via [a-z]+|grand total|total paid|total amount|amount paid|bill amount|order total|order amount|net amount|total|amount).{0,20}?([0-9,]+\\.[0-9]{2})",
            Pattern.CASE_INSENSITIVE),
        
        Pattern.compile(
            "(?:INR|Rs\\.?|₹)\\s*([0-9,]+(?:\\.[0-9]{2})?)",
            Pattern.CASE_INSENSITIVE),
        
        Pattern.compile(
            "\\b([0-9,]+\\.[0-9]{2})\\b"
        )
    );
    
    public BigDecimal extractAmount(String text) {
        if (text == null || text.trim().isEmpty()) {
            log.debug("Text is null or empty for amount extraction");
            return null;
        }

        try {
            for (int i = 0; i < AMOUNT_PATTERNS.size(); i++) {
                Pattern pattern = AMOUNT_PATTERNS.get(i);
                Matcher matcher = pattern.matcher(text);
                
                if (matcher.find()) {
                    String amountStr = matcher.group(1).replace(",", "").trim();
                    
                    if (amountStr.isEmpty()) {
                        continue;
                    }
                    
                    BigDecimal amount = new BigDecimal(amountStr);
                    
                    if (amount.compareTo(BigDecimal.ZERO) <= 0) {
                        log.debug("Skipping non-positive amount: {}", amount);
                        continue;
                    }
                    
                    log.debug("Extracted amount: {} using pattern {}", amount, i + 1);
                    return amount;
                }
            }
            
            log.debug("No amount found in text");
            return null;
            
        } catch (NumberFormatException e) {
            log.warn("Failed to parse amount: {}", e.getMessage());
            return null;
        }
    }
}
