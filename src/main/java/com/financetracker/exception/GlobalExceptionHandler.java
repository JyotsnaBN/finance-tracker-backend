package com.financetracker.exception;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.AuthenticationException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import jakarta.servlet.http.HttpServletRequest;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Data
@AllArgsConstructor
@NoArgsConstructor
class ErrorResponse {
    private int status;
    private String message;
    private LocalDateTime timestamp;
    private String path;
    private String errorId;
}

class ResourceNotFoundException extends RuntimeException {
    public ResourceNotFoundException(String message) {
        super(message);
    }
}

class InsufficientFundsException extends RuntimeException {
    public InsufficientFundsException(String message) {
        super(message);
    }
}

class UnauthorizedAccessException extends RuntimeException {
    public UnauthorizedAccessException(String message) {
        super(message);
    }
}

@RestControllerAdvice
public class GlobalExceptionHandler {
    
    private static final Logger logger = LoggerFactory.getLogger(GlobalExceptionHandler.class);
    
    private String generateErrorId() {
        return UUID.randomUUID().toString().substring(0, 8);
    }
    
    private ErrorResponse createErrorResponse(HttpStatus status, String message, String path) {
        String errorId = generateErrorId();
        return new ErrorResponse(
            status.value(),
            message,
            LocalDateTime.now(),
            path,
            errorId
        );
    }
    
    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleResourceNotFound(
            ResourceNotFoundException ex, HttpServletRequest request) {
        String errorId = generateErrorId();
        logger.warn("[{}] Resource not found at {}: {}", errorId, request.getRequestURI(), ex.getMessage());
        
        ErrorResponse error = createErrorResponse(
            HttpStatus.NOT_FOUND,
            ex.getMessage(),
            request.getRequestURI()
        );
        return new ResponseEntity<>(error, HttpStatus.NOT_FOUND);
    }
    
    @ExceptionHandler(UnauthorizedAccessException.class)
    public ResponseEntity<ErrorResponse> handleUnauthorizedAccess(
            UnauthorizedAccessException ex, HttpServletRequest request) {
        String errorId = generateErrorId();
        logger.warn("[{}] Unauthorized access attempt at {}: {}", errorId, request.getRequestURI(), ex.getMessage());
        
        ErrorResponse error = createErrorResponse(
            HttpStatus.FORBIDDEN,
            "You do not have permission to access this resource",
            request.getRequestURI()
        );
        return new ResponseEntity<>(error, HttpStatus.FORBIDDEN);
    }
    
    @ExceptionHandler(InsufficientFundsException.class)
    public ResponseEntity<ErrorResponse> handleInsufficientFunds(
            InsufficientFundsException ex, HttpServletRequest request) {
        String errorId = generateErrorId();
        logger.warn("[{}] Insufficient funds at {}: {}", errorId, request.getRequestURI(), ex.getMessage());
        
        ErrorResponse error = createErrorResponse(
            HttpStatus.BAD_REQUEST,
            ex.getMessage(),
            request.getRequestURI()
        );
        return new ResponseEntity<>(error, HttpStatus.BAD_REQUEST);
    }
    
    @ExceptionHandler(DuplicateResourceException.class)
    public ResponseEntity<ErrorResponse> handleDuplicateResource(
            DuplicateResourceException ex, HttpServletRequest request) {
        String errorId = generateErrorId();
        logger.warn("[{}] Duplicate resource at {}: {}", errorId, request.getRequestURI(), ex.getMessage());
        
        ErrorResponse error = createErrorResponse(
            HttpStatus.CONFLICT,
            ex.getMessage(),
            request.getRequestURI()
        );
        return new ResponseEntity<>(error, HttpStatus.CONFLICT);
    }
    
    @ExceptionHandler(EmailConfigurationException.class)
    public ResponseEntity<ErrorResponse> handleEmailConfiguration(
            EmailConfigurationException ex, HttpServletRequest request) {
        String errorId = generateErrorId();
        logger.error("[{}] Email configuration error at {}: {}", errorId, request.getRequestURI(), ex.getMessage(), ex);
        
        ErrorResponse error = createErrorResponse(
            HttpStatus.BAD_REQUEST,
            "Failed to configure email connection. Please try again or contact support if the problem persists.",
            request.getRequestURI()
        );
        return new ResponseEntity<>(error, HttpStatus.BAD_REQUEST);
    }
    
    @ExceptionHandler(OAuthException.class)
    public ResponseEntity<ErrorResponse> handleOAuth(
            OAuthException ex, HttpServletRequest request) {
        String errorId = generateErrorId();
        logger.error("[{}] OAuth error at {}: {}", errorId, request.getRequestURI(), ex.getMessage(), ex);
        
        ErrorResponse error = createErrorResponse(
            HttpStatus.UNAUTHORIZED,
            "Authentication failed. Please try connecting your email account again.",
            request.getRequestURI()
        );
        return new ResponseEntity<>(error, HttpStatus.UNAUTHORIZED);
    }
    
    @ExceptionHandler(EmailProcessingException.class)
    public ResponseEntity<ErrorResponse> handleEmailProcessing(
            EmailProcessingException ex, HttpServletRequest request) {
        String errorId = generateErrorId();
        logger.error("[{}] Email processing error at {}: {}", errorId, request.getRequestURI(), ex.getMessage(), ex);
        
        ErrorResponse error = createErrorResponse(
            HttpStatus.INTERNAL_SERVER_ERROR,
            "Failed to process email transactions. The issue has been logged and will be investigated.",
            request.getRequestURI()
        );
        return new ResponseEntity<>(error, HttpStatus.INTERNAL_SERVER_ERROR);
    }
    
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, String>> handleValidationExceptions(
            MethodArgumentNotValidException ex, HttpServletRequest request) {
        String errorId = generateErrorId();
        logger.warn("[{}] Validation failed at {}: {} validation errors",
            errorId, request.getRequestURI(), ex.getBindingResult().getErrorCount());
        
        Map<String, String> errors = new HashMap<>();
        ex.getBindingResult().getAllErrors().forEach((error) -> {
            String fieldName = ((FieldError) error).getField();
            String errorMessage = error.getDefaultMessage();
            errors.put(fieldName, errorMessage);
        });
        errors.put("errorId", errorId);
        return new ResponseEntity<>(errors, HttpStatus.BAD_REQUEST);
    }
    
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ErrorResponse> handleTypeMismatch(
            MethodArgumentTypeMismatchException ex, HttpServletRequest request) {
        String errorId = generateErrorId();
        logger.warn("[{}] Type mismatch at {}: parameter '{}' with value '{}' could not be converted to type '{}'",
            errorId, request.getRequestURI(), ex.getName(), ex.getValue(),
            ex.getRequiredType() != null ? ex.getRequiredType().getSimpleName() : "unknown");
        
        String message = String.format("Invalid value '%s' for parameter '%s'", ex.getValue(), ex.getName());
        ErrorResponse error = createErrorResponse(HttpStatus.BAD_REQUEST, message, request.getRequestURI());
        return new ResponseEntity<>(error, HttpStatus.BAD_REQUEST);
    }
    
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ErrorResponse> handleDataIntegrityViolation(
            DataIntegrityViolationException ex, HttpServletRequest request) {
        String errorId = generateErrorId();
        logger.error("[{}] Data integrity violation at {}: {}", errorId, request.getRequestURI(), ex.getMessage(), ex);
        
        String userMessage = "Unable to complete the operation due to data constraints";
        if (ex.getMessage() != null) {
            String msg = ex.getMessage().toLowerCase();
            if (msg.contains("duplicate entry") || msg.contains("unique constraint")) {
                userMessage = "This record already exists in the system";
            } else if (msg.contains("foreign key constraint") || msg.contains("cannot delete")) {
                userMessage = "Cannot complete operation because this record is referenced by other data";
            }
        }
        
        ErrorResponse error = createErrorResponse(HttpStatus.CONFLICT, userMessage, request.getRequestURI());
        return new ResponseEntity<>(error, HttpStatus.CONFLICT);
    }
    
    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ErrorResponse> handleAuthenticationException(
            AuthenticationException ex, HttpServletRequest request) {
        String errorId = generateErrorId();
        logger.warn("[{}] Authentication failed at {}: {}", errorId, request.getRequestURI(), ex.getMessage());
        
        ErrorResponse error = createErrorResponse(
            HttpStatus.UNAUTHORIZED,
            "Authentication failed. Please check your credentials and try again.",
            request.getRequestURI()
        );
        return new ResponseEntity<>(error, HttpStatus.UNAUTHORIZED);
    }
    
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorResponse> handleIllegalArgument(
            IllegalArgumentException ex, HttpServletRequest request) {
        String errorId = generateErrorId();
        logger.warn("[{}] Invalid argument at {}: {}", errorId, request.getRequestURI(), ex.getMessage());
        
        ErrorResponse error = createErrorResponse(
            HttpStatus.BAD_REQUEST,
            ex.getMessage() != null ? ex.getMessage() : "Invalid request parameters",
            request.getRequestURI()
        );
        return new ResponseEntity<>(error, HttpStatus.BAD_REQUEST);
    }
    
    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<ErrorResponse> handleRuntimeException(
            RuntimeException ex, HttpServletRequest request) {
        String errorId = generateErrorId();
        logger.error("[{}] Runtime exception at {}: {}", errorId, request.getRequestURI(), ex.getMessage(), ex);
        
        String userMessage = "An error occurred while processing your request";
        if (ex.getMessage() != null && !ex.getMessage().contains("Exception") &&
            !ex.getMessage().contains("Error") && ex.getMessage().length() < 200) {
            userMessage = ex.getMessage();
        }
        
        ErrorResponse error = createErrorResponse(HttpStatus.BAD_REQUEST, userMessage, request.getRequestURI());
        return new ResponseEntity<>(error, HttpStatus.BAD_REQUEST);
    }
    
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGenericException(
            Exception ex, HttpServletRequest request) {
        String errorId = generateErrorId();
        logger.error("[{}] Unexpected error at {}: {}", errorId, request.getRequestURI(), ex.getMessage(), ex);
        
        ErrorResponse error = createErrorResponse(
            HttpStatus.INTERNAL_SERVER_ERROR,
            "An unexpected error occurred. Our team has been notified. Please try again later or contact support with error ID: " + errorId,
            request.getRequestURI()
        );
        return new ResponseEntity<>(error, HttpStatus.INTERNAL_SERVER_ERROR);
    }
}
