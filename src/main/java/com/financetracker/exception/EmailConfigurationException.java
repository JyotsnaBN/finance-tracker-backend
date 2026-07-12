package com.financetracker.exception;

public class EmailConfigurationException extends RuntimeException {
    
    public EmailConfigurationException(String message) {
        super(message);
    }
    
    public EmailConfigurationException(String message, Throwable cause) {
        super(message, cause);
    }
}
