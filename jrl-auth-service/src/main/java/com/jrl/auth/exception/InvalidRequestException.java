package com.jrl.auth.exception;

/** Generic 400 for validation failures that don't fit Bean Validation
 *  (cross-field rules, JSON-content checks, etc.). */
public class InvalidRequestException extends RuntimeException {
    public InvalidRequestException(String message) {
        super(message);
    }
}
