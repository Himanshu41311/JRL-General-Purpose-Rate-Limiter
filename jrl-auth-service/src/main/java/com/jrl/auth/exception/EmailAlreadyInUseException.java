package com.jrl.auth.exception;

public class EmailAlreadyInUseException extends RuntimeException {
    public EmailAlreadyInUseException() {
        super("An account with this email already exists.");
    }
}
