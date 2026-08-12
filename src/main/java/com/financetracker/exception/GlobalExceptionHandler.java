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

    /** Generate an error ID, log the full exception, and return the ID so the user
     *  can quote it when contacting support — but never expose internal details. */
    private String logAndGetId(String level, String context, HttpServletRequest request, Throwable ex) {
        String errorId = generateErrorId();
        if ("error".equals(level)) {
            logger.error("[{}] {} at {}: {}", errorId, context, request.getRequestURI(), ex.getMessage(), ex);
        } else {
            logger.warn("[{}] {} at {}: {}", errorId, context, request.getRequestURI(), ex.getMessage());
        }
        return errorId;
    }

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleResourceNotFound(
            ResourceNotFoundException ex, HttpServletRequest request) {
        String errorId = logAndGetId("warn", "Resource not found", request, ex);
        ErrorResponse error = new ErrorResponse(
                HttpStatus.NOT_FOUND.value(), ex.getMessage(),
                LocalDateTime.now(), request.getRequestURI(), errorId);
        return new ResponseEntity<>(error, HttpStatus.NOT_FOUND);
    }

    @ExceptionHandler(UnauthorizedAccessException.class)
    public ResponseEntity<ErrorResponse> handleUnauthorizedAccess(
            UnauthorizedAccessException ex, HttpServletRequest request) {
        String errorId = logAndGetId("warn", "Unauthorized access attempt", request, ex);
        ErrorResponse error = new ErrorResponse(
                HttpStatus.FORBIDDEN.value(),
                "You do not have permission to access this resource",
                LocalDateTime.now(), request.getRequestURI(), errorId);
        return new ResponseEntity<>(error, HttpStatus.FORBIDDEN);
    }

    @ExceptionHandler(InsufficientFundsException.class)
    public ResponseEntity<ErrorResponse> handleInsufficientFunds(
            InsufficientFundsException ex, HttpServletRequest request) {
        String errorId = logAndGetId("warn", "Insufficient funds", request, ex);
        ErrorResponse error = new ErrorResponse(
                HttpStatus.BAD_REQUEST.value(), ex.getMessage(),
                LocalDateTime.now(), request.getRequestURI(), errorId);
        return new ResponseEntity<>(error, HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler(DuplicateResourceException.class)
    public ResponseEntity<ErrorResponse> handleDuplicateResource(
            DuplicateResourceException ex, HttpServletRequest request) {
        String errorId = logAndGetId("warn", "Duplicate resource", request, ex);
        ErrorResponse error = new ErrorResponse(
                HttpStatus.CONFLICT.value(), ex.getMessage(),
                LocalDateTime.now(), request.getRequestURI(), errorId);
        return new ResponseEntity<>(error, HttpStatus.CONFLICT);
    }

    @ExceptionHandler(EmailConfigurationException.class)
    public ResponseEntity<ErrorResponse> handleEmailConfiguration(
            EmailConfigurationException ex, HttpServletRequest request) {
        String errorId = logAndGetId("error", "Email configuration error", request, ex);
        ErrorResponse error = new ErrorResponse(
                HttpStatus.BAD_REQUEST.value(),
                "Failed to configure email connection. Please try again or contact support if the problem persists.",
                LocalDateTime.now(), request.getRequestURI(), errorId);
        return new ResponseEntity<>(error, HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler(OAuthException.class)
    public ResponseEntity<ErrorResponse> handleOAuth(
            OAuthException ex, HttpServletRequest request) {
        String errorId = logAndGetId("error", "OAuth error", request, ex);
        ErrorResponse error = new ErrorResponse(
                HttpStatus.UNAUTHORIZED.value(),
                "Authentication failed. Please try connecting your email account again.",
                LocalDateTime.now(), request.getRequestURI(), errorId);
        return new ResponseEntity<>(error, HttpStatus.UNAUTHORIZED);
    }

    @ExceptionHandler(EmailProcessingException.class)
    public ResponseEntity<ErrorResponse> handleEmailProcessing(
            EmailProcessingException ex, HttpServletRequest request) {
        String errorId = logAndGetId("error", "Email processing error", request, ex);
        ErrorResponse error = new ErrorResponse(
                HttpStatus.INTERNAL_SERVER_ERROR.value(),
                "Failed to process email transactions. The issue has been logged and will be investigated.",
                LocalDateTime.now(), request.getRequestURI(), errorId);
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
        logger.warn("[{}] Type mismatch at {}: parameter '{}'",
                errorId, request.getRequestURI(), ex.getName());

        // Redact the actual value from the user-facing message to avoid echoing back
        // potentially sensitive input.
        String message = String.format("Invalid value for parameter '%s'", ex.getName());
        ErrorResponse error = new ErrorResponse(
                HttpStatus.BAD_REQUEST.value(), message,
                LocalDateTime.now(), request.getRequestURI(), errorId);
        return new ResponseEntity<>(error, HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ErrorResponse> handleDataIntegrityViolation(
            DataIntegrityViolationException ex, HttpServletRequest request) {
        String errorId = logAndGetId("error", "Data integrity violation", request, ex);

        String userMessage = "Unable to complete the operation due to data constraints";
        if (ex.getMessage() != null) {
            String msg = ex.getMessage().toLowerCase();
            if (msg.contains("duplicate entry") || msg.contains("unique constraint")) {
                userMessage = "This record already exists in the system";
            } else if (msg.contains("foreign key constraint") || msg.contains("cannot delete")) {
                userMessage = "Cannot complete operation because this record is referenced by other data";
            }
        }
        // Use 409 Conflict — do NOT include raw DB error text
        ErrorResponse error = new ErrorResponse(
                HttpStatus.CONFLICT.value(), userMessage,
                LocalDateTime.now(), request.getRequestURI(), errorId);
        return new ResponseEntity<>(error, HttpStatus.CONFLICT);
    }

    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ErrorResponse> handleAuthenticationException(
            AuthenticationException ex, HttpServletRequest request) {
        String errorId = logAndGetId("warn", "Authentication failed", request, ex);
        ErrorResponse error = new ErrorResponse(
                HttpStatus.UNAUTHORIZED.value(),
                "Authentication failed. Please check your credentials and try again.",
                LocalDateTime.now(), request.getRequestURI(), errorId);
        return new ResponseEntity<>(error, HttpStatus.UNAUTHORIZED);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorResponse> handleIllegalArgument(
            IllegalArgumentException ex, HttpServletRequest request) {
        String errorId = logAndGetId("warn", "Invalid argument", request, ex);
        // Do NOT echo back ex.getMessage() — it may contain internal paths/values.
        ErrorResponse error = new ErrorResponse(
                HttpStatus.BAD_REQUEST.value(),
                "Invalid request parameters. Please check your input and try again.",
                LocalDateTime.now(), request.getRequestURI(), errorId);
        return new ResponseEntity<>(error, HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<ErrorResponse> handleRuntimeException(
            RuntimeException ex, HttpServletRequest request) {
        String errorId = logAndGetId("error", "Runtime exception", request, ex);
        // Always return a generic message — never forward internal exception text.
        ErrorResponse error = new ErrorResponse(
                HttpStatus.INTERNAL_SERVER_ERROR.value(),
                "An unexpected error occurred. Please try again later or contact support with error ID: " + errorId,
                LocalDateTime.now(), request.getRequestURI(), errorId);
        return new ResponseEntity<>(error, HttpStatus.INTERNAL_SERVER_ERROR);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGenericException(
            Exception ex, HttpServletRequest request) {
        String errorId = logAndGetId("error", "Unexpected error", request, ex);
        ErrorResponse error = new ErrorResponse(
                HttpStatus.INTERNAL_SERVER_ERROR.value(),
                "An unexpected error occurred. Our team has been notified. Please try again later or contact support with error ID: " + errorId,
                LocalDateTime.now(), request.getRequestURI(), errorId);
        return new ResponseEntity<>(error, HttpStatus.INTERNAL_SERVER_ERROR);
    }
}
