package com.financetracker.util;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

@Component
@Slf4j
public class ErrorMonitoringUtil {
    public void logCriticalError(String errorType, String message, Throwable throwable, Map<String, Object> context) {
        log.error("[CRITICAL] {} - {}", errorType, message, throwable);
        logErrorContext(errorType, context);
    }
    
    public void logWarningError(String errorType, String message, Map<String, Object> context) {
        log.warn("[WARNING] {} - {}", errorType, message);
        logErrorContext(errorType, context);
    }

    public void logInfoError(String errorType, String message, Map<String, Object> context) {
        log.info("[INFO] {} - {}", errorType, message);
        logErrorContext(errorType, context);
    }

    public void trackErrorMetric(String metricName, String errorType, Map<String, String> tags) {
        log.debug("Error metric tracked: {} - {} with tags: {}", metricName, errorType, tags);
    }
    
    public Map<String, Object> createErrorContext(String userId, String operation, String resource) {
        Map<String, Object> context = new HashMap<>();
        context.put("timestamp", Instant.now().toString());
        context.put("userId", userId);
        context.put("operation", operation);
        context.put("resource", resource);
        return context;
    }

    public void addContext(Map<String, Object> context, String key, Object value) {
        if (context != null && key != null) {
            context.put(key, value);
        }
    }
    
    private void logErrorContext(String errorType, Map<String, Object> context) {
        if (context != null && !context.isEmpty()) {
            log.debug("Error context for {}: {}", errorType, context);
        }
    }
    
    public boolean shouldAlert(String errorType) {
        return errorType.contains("CRITICAL") || 
               errorType.contains("SECURITY") || 
               errorType.contains("DATA_LOSS") ||
               errorType.contains("OAUTH_FAILURE");
    }
}

