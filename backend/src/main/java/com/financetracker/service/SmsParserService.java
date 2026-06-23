package com.financetracker.service;

import com.financetracker.dto.ParsedSmsResultDTO;
import com.financetracker.model.TransactionSource;
import com.financetracker.model.TransactionType;
import com.financetracker.util.TransactionParsingUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.Set;
import com.financetracker.util.AmountExtractor;
import org.springframework.beans.factory.annotation.Autowired;

@Service
@Slf4j
public class SmsParserService {

    @Autowired
    private AmountExtractor amountExtractor;

    private static final Pattern ACCOUNT_PATTERN =
        Pattern.compile(
                "(?:(?:A/c|Account)\\s*([Xx*]*\\d{3,6})|" +
                "(?:Credit\\s*Card\\s*(?:ending|xx|x{2,})\\s*([Xx*]*\\d{3,6}))|" +
                "(?:Credit\\s+Card\\s+No\\.?\\s*:?\\s*([Xx*]*\\d{4,}))|" +
                "(?:Card\\s+No\\.?\\s*:?\\s*([Xx*]*\\d{4,}))|" +
                "(?:Card\\s*(?:ending|no\\.?|number)\\s*[:\\s]*[Xx*]*?(\\d{4}))|" +
                "(?:(?:ending|last)\\s+(?:with|in)\\s+[Xx*]*(\\d{4}))|" +
                "(?:[Xx]{4,}(\\d{4})\\b))",
                Pattern.CASE_INSENSITIVE
        );
    private static final List<Pattern> BANK_ACCOUNT_TYPE_PATTERNS = List.of(
        Pattern.compile("\\b([A-Za-z]+\\s+Bank\\s+of\\s+[A-Za-z]+\\s+Credit\\s+Card)\\b", Pattern.CASE_INSENSITIVE),
        Pattern.compile("\\b([A-Za-z]+\\s+Bank(?:\\s+of\\s+[A-Za-z]+)?\\s+Credit\\s+Card)\\b", Pattern.CASE_INSENSITIVE),
        Pattern.compile("\\b([A-Za-z]+\\s+Bank(?:\\s+of\\s+[A-Za-z]+)?\\s+Savings)\\b", Pattern.CASE_INSENSITIVE),
        Pattern.compile("\\b([A-Za-z]+\\s+Bank(?:\\s+of\\s+[A-Za-z]+)?\\s+Current)\\b", Pattern.CASE_INSENSITIVE),
        Pattern.compile("\\b([A-Za-z]+\\s+Wallet)\\b", Pattern.CASE_INSENSITIVE),
        Pattern.compile("\\b(Credit\\s+Card\\s+of\\s+[A-Za-z]+\\s+Bank(?:\\s+of\\s+[A-Za-z]+)?)\\b", Pattern.CASE_INSENSITIVE)
    );

    private static final Pattern DATE_PATTERN =
        Pattern.compile("(\\d{1,2}-[A-Za-z]{3}-\\d{2,4}(?:\\s+\\d{2}:\\d{2}:\\d{2})?)", Pattern.CASE_INSENSITIVE);

    private static final Pattern CREDIT_CARD_MERCHANT_PATTERN =
            Pattern.compile(
                    "spent\\s+on\\s+Credit\\s+Card\\s+ending\\s+[Xx*\\d]+\\s+at\\s+(.+?)\\s+on\\s+\\d{1,2}-[A-Za-z]{3}-\\d{2,4}",
                    Pattern.CASE_INSENSITIVE
            );

    private static final Pattern PAYEE_DETAILS_PATTERN =
            Pattern.compile("Payee\\s+Details\\s*[:\\s]+([A-Za-z0-9&._\\-/ ]+?)(?:\\s*\\n|\\s*Channel|$)", Pattern.CASE_INSENSITIVE);
    
    private static final Pattern AT_MERCHANT_PATTERN =
            Pattern.compile("\\bat\\s+([A-Z][A-Za-z0-9&._\\-/ ]{2,30})(?:\\.\\s|\\s+on\\s|\\s+Avl|$)", Pattern.CASE_INSENSITIVE);

    private static final Pattern INFO_PATTERN =
            Pattern.compile("Info[:\\s]+([A-Za-z0-9/_\\- ]+?)(?:\\.\\s|\\s+Avl|$)", Pattern.CASE_INSENSITIVE);

    private static final Set<String> KNOWN_MERCHANTS = Set.of(
            "salary",
            "swiggy",
            "zomato",
            "uber",
            "ola",
            "amazon",
            "flipkart",
            "paytm",
            "phonepe",
            "google pay"
        );

    public ParsedSmsResultDTO parseSms(String smsText) {
        if (smsText == null || smsText.trim().isEmpty()) {
            log.warn("SMS text is null or empty");
            return createFailedParseResult(smsText, "Empty or null SMS text");
        }
        
        log.debug("Parsing SMS text of length: {}", smsText.length());

        try {
            BigDecimal amount = extractAmount(smsText);
            TransactionType type = detectTransactionType(smsText);
            Instant transactionDate = extractTransactionDate(smsText);
            String rawAccountHint = extractAccountHint(smsText);
            String accountHint = TransactionParsingUtil.normalizeAccountHint(rawAccountHint);
            String description = extractDescription(smsText);

            BigDecimal availableLimit = extractAvailableLimit(smsText);
            log.info("Extracted available limit: {}", availableLimit);

            boolean requiresManualReview =
                    amount == null || type == null || accountHint == null;

            String notes = buildParserNotes(amount, type, transactionDate, accountHint, description);

            return ParsedSmsResultDTO.builder()
                    .amount(amount)
                    .transactionType(type)
                    .transactionDate(transactionDate != null ? transactionDate : Instant.now())
                    .description(description != null ? description : "SMS transaction")
                    .rawText(smsText)
                    .source(TransactionSource.SMS)
                    .accountHint(accountHint)
                    .availableLimit(availableLimit)
                    .requiresManualReview(requiresManualReview)
                    .parserNotes(notes)
                    .build();
        } catch (Exception e) {
            log.error("Error parsing SMS: {}", e.getMessage());
            log.debug("SMS parsing error details:", e);
            return createFailedParseResult(smsText, "Parsing exception: " + e.getMessage());
        }
    }
    
    private ParsedSmsResultDTO createFailedParseResult(String smsText, String reason) {
        return ParsedSmsResultDTO.builder()
                .amount(null)
                .transactionType(null)
                .transactionDate(Instant.now())
                .description("Failed to parse SMS")
                .rawText(smsText)
                .source(TransactionSource.SMS)
                .accountHint(null)
                .requiresManualReview(true)
                .parserNotes("parse_failed;" + reason)
                .build();
    }

    public TransactionType detectTransactionType(String text) {
        if (!TransactionParsingUtil.isValidString(text)) {
            log.debug("Text is null or empty for transaction type detection");
            return null;
        }

        try {
            String lower = text.toLowerCase();

            if (lower.contains("credit card") && 
                (lower.contains("transaction") || lower.contains("processed"))) {
                log.debug("Detected credit card transaction - treating as DEBIT");
                return TransactionType.DEBIT;
            }

            if (TransactionParsingUtil.containsAny(lower, "debited", "debit", "paid", "spent",
                    "withdrawn", "sent", "used", "purchase", "payment")) {
                return TransactionType.DEBIT;
            }

            if (TransactionParsingUtil.containsAny(lower, "credited", "credit", "received",
                    "deposited", "added", "refund", "cashback")) {
                return TransactionType.CREDIT;
            }

            log.debug("No transaction type keywords found in text");
            return null;
        } catch (Exception e) {
            log.warn("Error detecting transaction type: {}", e.getMessage());
            return null;
        }
    }

    public BigDecimal extractAmount(String text) {
        return amountExtractor.extractAmount(text);
    }


    public BigDecimal extractAvailableLimit(String text) {
        if (!TransactionParsingUtil.isValidString(text)) {
            return null;
        }
        
        try {
            Pattern limitPattern = Pattern.compile(
                "(?:Available\\s+Limit|Avl\\s+Lmt|Avl\\s+Limit)[:\\s]+(?:Rs\\.?\\s*)?(\\d+(?:,\\d+)*(?:\\.\\d{1,2})?)",
                Pattern.CASE_INSENSITIVE
            );
            
            Matcher matcher = limitPattern.matcher(text);
            if (matcher.find()) {
                String limitStr = matcher.group(1).replace(",", "").trim();
                BigDecimal limit = new BigDecimal(limitStr);
                log.debug("Extracted available limit: {}", limit);
                return limit;
            }
            
            log.debug("No available limit found in text");
            return null;
        } catch (Exception e) {
            log.warn("Error extracting available limit: {}", e.getMessage());
            return null;
        }
    }

    public Instant extractTransactionDate(String text) {
        if (!TransactionParsingUtil.isValidString(text)) {
            log.debug("Text is null or empty for date extraction");
            return null;
        }

        try {
            Matcher matcher = DATE_PATTERN.matcher(text);
            if (!matcher.find()) {
                log.debug("No date pattern matched in text");
                return null;
            }

            String value = matcher.group(1).trim();
            String normalizedValue = normalizeDateMonth(value);

            for (DateTimeFormatter formatter : List.of(
                    DateTimeFormatter.ofPattern("d-MMM-yy HH:mm:ss", Locale.ENGLISH),
                    DateTimeFormatter.ofPattern("d-MMM-yyyy HH:mm:ss", Locale.ENGLISH),
                    DateTimeFormatter.ofPattern("d-MMM-yy", Locale.ENGLISH),
                    DateTimeFormatter.ofPattern("d-MMM-yyyy", Locale.ENGLISH)
            )) {
                try {
                    Instant parsedDate;
                    if (normalizedValue.contains(":")) {
                        LocalDateTime dateTime = LocalDateTime.parse(normalizedValue, formatter);
                        parsedDate = dateTime.atZone(ZoneId.systemDefault()).toInstant();
                    } else {
                        LocalDate date = LocalDate.parse(normalizedValue, formatter);
                        parsedDate = date.atStartOfDay(ZoneId.systemDefault()).toInstant();
                    }
                    
                    Instant now = Instant.now();
                    Instant oneYearAgo = now.minus(365, ChronoUnit.DAYS);
                    
                    if (parsedDate.isAfter(now)) {
                        log.warn("Extracted future date: {}, using current time", parsedDate);
                        return now;
                    }
                    
                    if (parsedDate.isBefore(oneYearAgo)) {
                        log.warn("Extracted date older than 1 year: {}", parsedDate);
                    }
                    
                    log.debug("Extracted transaction date: {}", parsedDate);
                    return parsedDate;
                } catch (DateTimeParseException ignored) {
                }
            }

            log.debug("Failed to parse date with any formatter: {}", normalizedValue);
            return null;
        } catch (Exception e) {
            log.error("Error extracting transaction date: {}", e.getMessage());
            return null;
        }
    }

    private String normalizeDateMonth(String value) {
        if (value == null || value.isEmpty()) {
            return value;
        }
        
        try {
            Matcher matcher = Pattern.compile("(\\d{1,2})-([A-Za-z]{3})-(\\d{2,4})(.*)").matcher(value);
            if (!matcher.matches()) {
                return value;
            }

            String month = matcher.group(2);
            if (month.length() < 3) {
                return value;
            }
            
            String normalizedMonth = month.substring(0, 1).toUpperCase(Locale.ENGLISH)
                    + month.substring(1, 3).toLowerCase(Locale.ENGLISH);

            return matcher.group(1) + "-" + normalizedMonth + "-" + matcher.group(3) + matcher.group(4);
        } catch (Exception e) {
            log.warn("Error normalizing date month for '{}': {}", value, e.getMessage());
            return value;
        }
    }
    
    public String extractAccountHint(String text) {
        if (!TransactionParsingUtil.isValidString(text)) {
            log.debug("Text is null or empty for account hint extraction");
            return null;
        }

        try {
            Matcher matcher = ACCOUNT_PATTERN.matcher(text);
            if (matcher.find()) {
                for (int i = 1; i <= matcher.groupCount(); i++) {
                    String group = matcher.group(i);
                    if (group != null && !group.trim().isEmpty()) {
                        log.debug("Extracted account hint from group {}: {}", i, group);
                        return group.trim();
                    }
                }
            }
            
            String bankTypeHint = extractBankAccountTypeHint(text);
            if (bankTypeHint != null) {
                log.debug("Extracted bank account type hint: {}", bankTypeHint);
            }
            return bankTypeHint;
        } catch (Exception e) {
            log.error("Error extracting account hint: {}", e.getMessage());
            return null;
        }
    }

    private String extractBankAccountTypeHint(String text) {
        if (!TransactionParsingUtil.isValidString(text)) {
            return null;
        }

        try {
            for (Pattern pattern : BANK_ACCOUNT_TYPE_PATTERNS) {
                Matcher matcher = pattern.matcher(text);
                if (matcher.find()) {
                    String hint = matcher.group(1).trim().replaceAll("\\s+", " ");
                    log.debug("Extracted bank account type hint: {}", hint);
                    return hint;
                }
            }

            log.debug("No bank account type hint found");
            return null;
        } catch (Exception e) {
            log.warn("Error extracting bank account type hint: {}", e.getMessage());
            return null;
        }
    }

    public String extractDescription(String text) {
        if (!TransactionParsingUtil.isValidString(text)) {
            log.debug("Text is null or empty for description extraction");
            return "Unknown transaction";
        }

        try {
            Matcher payeeMatcher = PAYEE_DETAILS_PATTERN.matcher(text);
            if (payeeMatcher.find()) {
                String description = TransactionParsingUtil.cleanDescription(payeeMatcher.group(1));
                if (description != null) {
                    log.debug("Extracted description from Payee Details: {}", description);
                    return description;
                }
            }
            
            Matcher ccMatcher = CREDIT_CARD_MERCHANT_PATTERN.matcher(text);
            if (ccMatcher.find()) {
                String merchant = ccMatcher.group(1).trim();
                if (!merchant.isEmpty()) {
                    String description = TransactionParsingUtil.cleanDescription(merchant);
                    log.debug("Extracted description from CC merchant: {}", description);
                    return description != null ? description : merchant.toUpperCase();
                }
            }

            Matcher infoMatcher = INFO_PATTERN.matcher(text);
            if (infoMatcher.find()) {
                String description = TransactionParsingUtil.cleanDescription(infoMatcher.group(1));
                if (description != null) {
                    log.debug("Extracted description from Info: {}", description);
                    return description;
                }
            }

            Matcher atMatcher = AT_MERCHANT_PATTERN.matcher(text);
            if (atMatcher.find()) {
                String description = TransactionParsingUtil.cleanDescription(atMatcher.group(1));
                if (description != null) {
                    log.debug("Extracted description from 'at' pattern: {}", description);
                    return description;
                }
            }

            String lower = text.toLowerCase();
            for (String merchant : KNOWN_MERCHANTS) {
                if (lower.contains(merchant)) {
                    log.debug("Extracted known merchant: {}", merchant);
                    return merchant.toUpperCase();
                }
            }

            log.debug("No specific description found, using default");
            return "SMS parsed transaction";
        } catch (Exception e) {
            log.error("Error extracting description: {}", e.getMessage());
            return "SMS transaction";
        }
    }
    private String buildParserNotes(
            BigDecimal amount,
            TransactionType type,
            Instant transactionDate,
            String accountHint,
            String description
    ) {
        StringBuilder notes = new StringBuilder();

        try {
            if (amount == null) {
                notes.append("amount_missing;");
            } else if (amount.compareTo(BigDecimal.ZERO) <= 0) {
                notes.append("invalid_amount;");
            }
            
            if (type == null) {
                notes.append("type_missing;");
            }
            
            if (transactionDate == null) {
                notes.append("date_missing;");
            } else {
                Instant now = Instant.now();
                if (transactionDate.isAfter(now)) {
                    notes.append("future_date;");
                }
            }
            
            if (!TransactionParsingUtil.isValidString(accountHint)) {
                notes.append("account_hint_missing;");
            }
            
            if (!TransactionParsingUtil.isValidString(description)) {
                notes.append("description_missing;");
            }

            String result = notes.toString();
            if (!result.isEmpty()) {
                log.debug("Parser notes: {}", result);
            }
            return result;
        } catch (Exception e) {
            log.warn("Error building parser notes: {}", e.getMessage());
            return "notes_error;";
        }
    }

}
