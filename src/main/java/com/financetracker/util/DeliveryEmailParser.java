package com.financetracker.util;

import com.financetracker.dto.DeliveryEventDTO;
import com.financetracker.dto.OrderItemDTO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.text.Normalizer;

@Component
@Slf4j
public class DeliveryEmailParser {

    @Autowired
    private AmountExtractor amountExtractor;    
    
    private static final List<String> DELIVERY_KEYWORDS = Arrays.asList(
        "delivered", "delivery confirmation", "package delivered",
        "order delivered", "shipment delivered", "has been delivered",
        "successfully delivered", "delivery complete"
    );
    
    private static final List<String> MERCHANT_KEYWORDS = Arrays.asList(
        "swiggy", "zomato", "instamart", "amazon", "flipkart",
        "myntra", "meesho", "blinkit", "zepto", "dunzo"
    );
    
    private static final List<String> EXCLUDED_ITEMS = Arrays.asList(
        "packaging", "platform fee", "delivery fee", "delivery charge",
        "taxes", "gst", "handling", "convenience fee", "service charge"
    );
    
    private static final Pattern ORDER_ID_PATTERN = Pattern.compile(
        "order[\\s#:id]+([A-Z0-9]{8,30})", Pattern.CASE_INSENSITIVE);
    
    private static final Pattern AMOUNT_PATTERN = Pattern.compile(
        "(?:paid via [a-z]+|grand total|total paid|total|amount)[\\s:₹Rs.$]*([0-9,]+(?:\\.[0-9]{2})?)",
        Pattern.CASE_INSENSITIVE);
    
    private static final Pattern SAVINGS_PATTERN = Pattern.compile(
        "₹?\\s*([0-9,]+)\\s*(?:saved|discount|off)", Pattern.CASE_INSENSITIVE);
    
    private static final Pattern DELIVERY_TIME_PATTERN = Pattern.compile(
        "delivery in (\\d+)\\s*(min|mins|minutes|hour|hours|hr|hrs)",
        Pattern.CASE_INSENSITIVE);
    
    private static final List<Pattern> DATE_PATTERNS = Arrays.asList(
        Pattern.compile(
            "\\bdate:\\s*(?:(?:sun|mon|tue|wed|thu|fri|sat)[a-zA-Z]*,?\\s+)?" +
            "(jan|feb|mar|apr|may|jun|jul|aug|sep|oct|nov|dec)[a-zA-Z]*\\.?\\s+" +
            "(\\d{1,2}),?\\s+" +
            "(\\d{4})\\s*(?:at)?\\s*" +
            "(\\d{1,2}):(\\d{2})\\s*(am|pm)",
            Pattern.CASE_INSENSITIVE | Pattern.MULTILINE),
        Pattern.compile(
            "(?:(?:order\\s+)?date:\\s*)?(?:(?:sun|mon|tue|wed|thu|fri|sat)[a-zA-Z]*,?\\s+)?" +
            "(jan|feb|mar|apr|may|jun|jul|aug|sep|oct|nov|dec)[a-zA-Z]*\\.?\\s+" +
            "(\\d{1,2}),?\\s+" +
            "(\\d{4})\\s*(?:at)?\\s*" +
            "(\\d{1,2}):(\\d{2})\\s*(am|pm)",
            Pattern.CASE_INSENSITIVE | Pattern.MULTILINE),
        
        Pattern.compile(
            "(?:(?:sun|mon|tue|wed|thu|fri|sat)[a-z]*,?\\s+)?" +
            "(jan|feb|mar|apr|may|jun|jul|aug|sep|oct|nov|dec)[a-z]*\\.?\\s+" +
            "(\\d{1,2}),?\\s*(?:at)?\\s*" +
            "(\\d{1,2}):(\\d{2})\\s*(am|pm)",
            Pattern.CASE_INSENSITIVE | Pattern.MULTILINE),
        
        Pattern.compile(
            "(\\d{1,2})\\s+" +
            "(jan|feb|mar|apr|may|jun|jul|aug|sep|oct|nov|dec)[a-z]*\\.?\\s+" +
            "(\\d{4}),?\\s*(?:at)?\\s*" +
            "(\\d{1,2}):(\\d{2})\\s*(am|pm)",
            Pattern.CASE_INSENSITIVE | Pattern.MULTILINE),
        
        Pattern.compile(
            "(\\d{4})[-/](\\d{1,2})[-/](\\d{1,2})\\s+" +
            "(\\d{1,2}):(\\d{2})",
            Pattern.CASE_INSENSITIVE | Pattern.MULTILINE),
        
        Pattern.compile(
            "(\\d{1,2}):(\\d{2})\\s+(am|pm)",
            Pattern.CASE_INSENSITIVE | Pattern.MULTILINE)
    );
        
    private static final List<Pattern> ITEM_PATTERNS = Arrays.asList(
        Pattern.compile("([A-Za-z0-9\\s&'\\-|().,]+?)\\s+x(\\d+)\\s+₹([0-9,]+(?:\\.[0-9]{2})?)",
            Pattern.CASE_INSENSITIVE),
        Pattern.compile("(\\d+)\\s*x\\s+([^₹\\n]{3,100}?)\\s*₹([0-9,]+(?:\\.[0-9]{2})?)",
            Pattern.CASE_INSENSITIVE),
        Pattern.compile("([A-Za-z0-9\\s&'\\-|().,]+?)\\s*-\\s*qty[:\\s]*(\\d+)\\s*-\\s*price[:\\s]*₹([0-9,]+(?:\\.[0-9]{2})?)",
            Pattern.CASE_INSENSITIVE)
    );
    
    public boolean isDeliveryEmail(String subject, String body) {
        String combined = (subject + " " + body).toLowerCase();
        return DELIVERY_KEYWORDS.stream().anyMatch(combined::contains);
    }
    
    public DeliveryEventDTO parseDeliveryEmail(String subject, String body, Instant emailReceivedDate) {
        if (!isDeliveryEmail(subject, body)) {
            return null;
        }
        
        String combined = subject + "\n" + body;
        
        DeliveryEventDTO.DeliveryEventDTOBuilder builder = DeliveryEventDTO.builder();
        
        extractOrderId(combined).ifPresent(builder::trackingNumber);
        
        extractMerchant(combined).ifPresent(builder::merchant);
        
        extractStoreName(body).ifPresent(builder::storeName);
        
        builder.deliveryDate(extractDeliveryDate(combined, emailReceivedDate));
        
        extractDeliveryDuration(combined).ifPresent(builder::deliveryDuration);
        
        extractSavings(combined).ifPresent(builder::savingsAmount);
        
        List<OrderItemDTO> items = extractItems(body);
        if (!items.isEmpty()) {
            builder.items(items);
            builder.itemCount(items.size());
        }
        
        builder.emailProcessedAt(Instant.now());
        
        DeliveryEventDTO event = builder.build();
        
        if (event.getTrackingNumber() == null && event.getMerchant() == null) {
            log.warn("Could not extract sufficient delivery information");
            return null;
        }
        
        return event;
    }
    
    public BigDecimal extractAmount(String subject, String body) {
        String combined = subject + "\n" + body;
        return amountExtractor.extractAmount(combined);
    }
    
    private Optional<String> extractOrderId(String text) {
        Matcher matcher = ORDER_ID_PATTERN.matcher(text);
        return matcher.find() ? Optional.of(matcher.group(1).trim()) : Optional.empty();
    }
    
    private Optional<String> extractMerchant(String text) {
        String lowerText = text.toLowerCase();
        return MERCHANT_KEYWORDS.stream()
            .filter(lowerText::contains)
            .findFirst()
            .map(m -> m.substring(0, 1).toUpperCase() + m.substring(1));
    }
    
    private Optional<String> extractStoreName(String body) {
        List<Pattern> storePatterns = Arrays.asList(
            Pattern.compile("(?:restaurant icon|from|store)\\s*\\n?\\s*([A-Za-z0-9\\s'\\-&]+?)(?:\\n|\\(|$)",
                Pattern.CASE_INSENSITIVE),
            Pattern.compile("(?:greetings from|order from)\\s+([A-Za-z0-9\\s'\\-&]+?)(?:\\n|$)",
                Pattern.CASE_INSENSITIVE)
        );
        
        for (Pattern pattern : storePatterns) {
            Matcher matcher = pattern.matcher(body);
            if (matcher.find()) {
                String storeName = matcher.group(1).trim();
                if (storeName.length() > 3 && storeName.length() < 100) {
                    return Optional.of(storeName);
                }
            }
        }
        return Optional.empty();
    }

    private Instant extractDeliveryDate(String text, Instant emailReceivedDate) {
        text = Normalizer
            .normalize(text, Normalizer.Form.NFKC)
            .replaceAll("\\p{Z}", " ")
            .replaceAll("\\s+", " ");

        Map<String, Integer> monthMap = Map.ofEntries(
            Map.entry("jan", 1), Map.entry("feb", 2), Map.entry("mar", 3),
            Map.entry("apr", 4), Map.entry("may", 5), Map.entry("jun", 6),
            Map.entry("jul", 7), Map.entry("aug", 8), Map.entry("sep", 9),
            Map.entry("oct", 10), Map.entry("nov", 11), Map.entry("dec", 12)
        );
        
        int currentYear = emailReceivedDate != null
                ? LocalDateTime.ofInstant(emailReceivedDate, ZoneId.systemDefault()).getYear()
                : LocalDate.now().getYear();

        LocalDate currentDate = LocalDate.now();

        for (int i = 0; i < DATE_PATTERNS.size(); i++) {
            Pattern pattern = DATE_PATTERNS.get(i);
            Matcher matcher = pattern.matcher(text);
            
            if (matcher.find()) {

                try {
                    int year, month, day, hour, minute;
                    String ampm = null;
                    
                    switch (i) {
                        case 0:
                            month = monthMap.get(matcher.group(1).toLowerCase().substring(0, 3));
                            day = Integer.parseInt(matcher.group(2));
                            year = Integer.parseInt(matcher.group(3));
                            hour = Integer.parseInt(matcher.group(4));
                            minute = Integer.parseInt(matcher.group(5));
                            ampm = matcher.group(6);
                            break;
                            
                        case 1:
                            month = monthMap.get(matcher.group(1).toLowerCase().substring(0, 3));
                            day = Integer.parseInt(matcher.group(2));
                            year = Integer.parseInt(matcher.group(3));
                            hour = Integer.parseInt(matcher.group(4));
                            minute = Integer.parseInt(matcher.group(5));
                            ampm = matcher.group(6);
                            break;
                            
                        case 2:
                            month = monthMap.get(matcher.group(1).toLowerCase().substring(0, 3));
                            day = Integer.parseInt(matcher.group(2));
                            year = currentYear;
                            hour = Integer.parseInt(matcher.group(3));
                            minute = Integer.parseInt(matcher.group(4));
                            ampm = matcher.group(5);
                            break;

                        case 3:
                            day = Integer.parseInt(matcher.group(1));
                            month = monthMap.get(matcher.group(2).toLowerCase().substring(0, 3));
                            year = Integer.parseInt(matcher.group(3));
                            hour = Integer.parseInt(matcher.group(4));
                            minute = Integer.parseInt(matcher.group(5));
                            ampm = matcher.group(6);
                            break;
                            
                        case 4:
                            year = Integer.parseInt(matcher.group(1));
                            month = Integer.parseInt(matcher.group(2));
                            day = Integer.parseInt(matcher.group(3));
                            hour = Integer.parseInt(matcher.group(4));
                            minute = Integer.parseInt(matcher.group(5));
                            ampm = null;
                            break;
                            
                        case 5:
                            year = currentDate.getYear();
                            month = currentDate.getMonthValue();
                            day = currentDate.getDayOfMonth();
                            hour = Integer.parseInt(matcher.group(1));
                            minute = Integer.parseInt(matcher.group(2));
                            ampm = matcher.group(3);
                            break;
                            
                        default:
                            continue;
                    }
                    
                    if (ampm != null) {
                        if (ampm.equalsIgnoreCase("pm") && hour != 12) {
                            hour += 12;
                        } else if (ampm.equalsIgnoreCase("am") && hour == 12) {
                            hour = 0;
                        }
                    }
                    
                    LocalDateTime dateTime = LocalDateTime.of(year, month, day, hour, minute);
                    Instant result = dateTime.atZone(ZoneId.systemDefault()).toInstant();
                    log.info("✓ Parsed delivery date from email content: {}", result);
                    log.info("  Extracted values: year={}, month={}, day={}, hour={}, minute={}", 
                        year, month, day, hour, minute);
                    return result;
                    
                } catch (Exception e) {
                    log.debug("Pattern {} failed to parse: {}", i, e.getMessage());
                    continue;
                }
            }
        }
        
        if (emailReceivedDate != null) {
            log.info("→ Using email received date as fallback: {}", emailReceivedDate);
            return emailReceivedDate;
        }
        
        log.warn("⚠ No delivery date found, using current time");
        return Instant.now();
    }
        
    private Optional<String> extractDeliveryDuration(String text) {
        Matcher matcher = DELIVERY_TIME_PATTERN.matcher(text);
        if (matcher.find()) {
            String duration = matcher.group(1);
            String unit = matcher.group(2);
            return Optional.of(duration + " " + normalizeTimeUnit(unit));
        }
        return Optional.empty();
    }
    
    private String normalizeTimeUnit(String unit) {
        unit = unit.toLowerCase();
        if (unit.startsWith("min")) return "mins";
        if (unit.startsWith("hour") || unit.startsWith("hr")) return "hours";
        return unit;
    }
    
    private Optional<BigDecimal> extractSavings(String text) {
        Matcher matcher = SAVINGS_PATTERN.matcher(text);
        if (matcher.find()) {
            try {
                String amountStr = matcher.group(1).replace(",", "");
                return Optional.of(new BigDecimal(amountStr));
            } catch (NumberFormatException e) {
                log.warn("Failed to parse savings amount: {}", matcher.group(1));
            }
        }
        return Optional.empty();
    }
    
    private List<OrderItemDTO> extractItems(String body) {
        List<OrderItemDTO> items = new ArrayList<>();
        
        for (Pattern pattern : ITEM_PATTERNS) {
            Matcher matcher = pattern.matcher(body);
            
            while (matcher.find()) {
                try {
                    String name;
                    int quantity;
                    BigDecimal price;
                    
                    if (pattern.pattern().startsWith("(\\d+)")) {
                        quantity = Integer.parseInt(matcher.group(1));
                        name = matcher.group(2).trim();
                        price = new BigDecimal(matcher.group(3).replace(",", ""));
                    } else {
                        name = matcher.group(1).trim();
                        quantity = Integer.parseInt(matcher.group(2));
                        price = new BigDecimal(matcher.group(3).replace(",", ""));
                    }
                    
                    if (shouldExcludeItem(name)) {
                        continue;
                    }
                    
                    items.add(OrderItemDTO.builder()
                        .name(truncateString(name, 100))
                        .quantity(quantity)
                        .price(price)
                        .build());
                        
                } catch (Exception e) {
                    log.debug("Failed to parse item: {}", e.getMessage());
                }
            }
            
            if (!items.isEmpty()) {
                break;
            }
        }
        
        return items;
    }
    
    private boolean shouldExcludeItem(String itemName) {
        String lowerName = itemName.toLowerCase();
        return EXCLUDED_ITEMS.stream().anyMatch(lowerName::contains);
    }
    
    private String truncateString(String str, int maxLength) {
        if (str == null) return null;
        return str.length() > maxLength ? str.substring(0, maxLength - 3) + "..." : str;
    }
}
