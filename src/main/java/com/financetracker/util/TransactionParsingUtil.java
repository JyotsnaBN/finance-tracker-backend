package com.financetracker.util;
import com.financetracker.model.AccountType;
import lombok.extern.slf4j.Slf4j;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
 
@Slf4j
public class TransactionParsingUtil {
    private static final Pattern LAST_4_DIGITS_PATTERN = Pattern.compile("\\d{4}");
    
    private static final String[][] BANK_PATTERNS = {
        {"state bank", "sbi", "State Bank of India"},
        {"hdfc", "HDFC Bank"},
        {"icici", "ICICI Bank"},
        {"axis", "Axis Bank"},
        {"kotak", "Kotak Mahindra Bank"},
        {"paytm", "Paytm Payments Bank"},
        {"bank of india", "boi", "Bank of India"},
        {"punjab national", "pnb", "Punjab National Bank"},
        {"canara", "Canara Bank"},
        {"union bank", "Union Bank of India"},
        {"idbi", "IDBI Bank"},
        {"yes bank", "Yes Bank"},
        {"indusind", "IndusInd Bank"},
        {"bank of baroda", "bob", "Bank of Baroda"},
        {"central bank", "Central Bank of India"},
        {"indian bank", "Indian Bank"},
        {"uco bank", "UCO Bank"},
        {"punjab and sind", "Punjab & Sind Bank"},
        {"indian overseas", "Indian Overseas Bank"}
    };
    private TransactionParsingUtil() {
    }
    
     
    public static String extractLast4Digits(String accountHint) {
        if (accountHint == null || accountHint.trim().isEmpty()) {
            log.debug("Account hint is null or empty");
            return null;
        }
        try {
            String cleaned = accountHint.trim();
            
            cleaned = cleaned.replaceAll("[Xx*\\-\\s]", "");
            
            Matcher matcher = LAST_4_DIGITS_PATTERN.matcher(cleaned);
            String lastMatch = null;
            
            while (matcher.find()) {
                lastMatch = matcher.group();
            }
            
            if (lastMatch != null && lastMatch.length() >= 4) {
                String result = lastMatch.substring(lastMatch.length() - 4);
                log.debug("Extracted last 4 digits: {} from hint: {}", result, accountHint);
                return result;
            }
            
            log.debug("No 4-digit sequence found in account hint: {}", accountHint);
            return null;
            
        } catch (Exception e) {
            log.warn("Error extracting last 4 digits from '{}': {}", accountHint, e.getMessage());
            return null;
        }
    }
    
     
    public static String extractBankName(String text) {
        if (text == null || text.trim().isEmpty()) {
            log.debug("Text is null or empty for bank name extraction");
            return null;
        }
        try {
            String lower = text.toLowerCase().trim();
            
            for (String[] bankPattern : BANK_PATTERNS) {
                for (int i = 0; i < bankPattern.length - 1; i++) {
                    if (lower.contains(bankPattern[i])) {
                        String standardName = bankPattern[bankPattern.length - 1];
                        log.debug("Extracted bank name: {} from text containing: {}", standardName, bankPattern[i]);
                        return standardName;
                    }
                }
            }
            
            log.debug("No known bank name found in text: {}", text);
            return null;
            
        } catch (Exception e) {
            log.warn("Error extracting bank name from '{}': {}", text, e.getMessage());
            return null;
        }
    }
    
     
    public static AccountType extractAccountType(String text) {
        if (text == null || text.trim().isEmpty()) {
            log.debug("Text is null or empty for account type extraction");
            return null;
        }
        try {
            String lower = text.toLowerCase().trim();
            
            if (lower.contains("credit card") || lower.contains("credit-card") || 
                lower.contains("cc ") || lower.matches(".*\\bcc\\b.*")) {
                log.debug("Extracted account type: CREDIT_CARD from text");
                return AccountType.CREDIT_CARD;
            }
            
            if (lower.contains("wallet") || lower.contains("paytm") || 
                lower.contains("phonepe") || lower.contains("google pay")) {
                log.debug("Extracted account type: WALLET from text");
                return AccountType.WALLET;
            }
            
            if (lower.contains("savings") || lower.contains("saving") || 
                lower.contains("sb ") || lower.matches(".*\\bsb\\b.*")) {
                log.debug("Extracted account type: SAVINGS from text");
                return AccountType.SAVINGS;
            }
            
            if (lower.contains("current") || lower.contains("ca ") || 
                lower.matches(".*\\bca\\b.*")) {
                log.debug("Extracted account type: CURRENT from text");
                return AccountType.CURRENT;
            }
            
            log.debug("No account type found in text: {}", text);
            return null;
            
        } catch (Exception e) {
            log.warn("Error extracting account type from '{}': {}", text, e.getMessage());
            return null;
        }
    }
    
     
    public static String truncateString(String value, int maxLength) {
        if (value == null) {
            return null;
        }
        
        if (maxLength < 4) {
            log.warn("maxLength {} is too small for meaningful truncation", maxLength);
            return value.substring(0, Math.min(value.length(), maxLength));
        }
        
        if (value.length() <= maxLength) {
            return value;
        }
        
        String truncated = value.substring(0, maxLength - 3) + "...";
        log.debug("Truncated string from {} to {} characters", value.length(), truncated.length());
        return truncated;
    }
    
     
    public static String normalizeAccountHint(String rawHint) {
        if (rawHint == null || rawHint.trim().isEmpty()) {
            return null;
        }
        try {
            String last4 = extractLast4Digits(rawHint);
            if (last4 != null) {
                return last4;
            }
            String cleaned = rawHint.trim().replaceAll("\\s+", " ");
            log.debug("Normalized account hint (no digits): {}", cleaned);
            return cleaned;
            
        } catch (Exception e) {
            log.warn("Error normalizing account hint '{}': {}", rawHint, e.getMessage());
            return rawHint.trim();
        }
    }
    
     
    public static boolean containsAny(String text, String... keywords) {
        if (text == null || text.isEmpty() || keywords == null || keywords.length == 0) {
            return false;
        }
        String lower = text.toLowerCase();
        for (String keyword : keywords) {
            if (keyword != null && lower.contains(keyword.toLowerCase())) {
                return true;
            }
        }
        return false;
    }
    
     
    public static String cleanDescription(String description) {
        if (description == null || description.trim().isEmpty()) {
            return null;
        }
        try {
            return description.trim()
                .replaceAll("\\s+", " ")
                .replaceAll("[^a-zA-Z0-9\\s\\-_&./]", "")
                .toUpperCase();
        } catch (Exception e) {
            log.warn("Error cleaning description '{}': {}", description, e.getMessage());
            return description.trim().toUpperCase();
        }
    }
    
     
    public static boolean isValidString(String value) {
        return value != null && !value.trim().isEmpty();
    }
    
     
    public static String safeSubstring(String text, int start, int end) {
        if (text == null || start < 0 || end > text.length() || start >= end) {
            return "";
        }
        
        try {
            return text.substring(start, end);
        } catch (Exception e) {
            log.warn("Error extracting substring from index {} to {}: {}", start, end, e.getMessage());
            return "";
        }
    }
}
