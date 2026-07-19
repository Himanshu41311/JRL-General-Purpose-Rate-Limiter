package com.jrl.auth.exception;

public class UserNotFoundException extends RuntimeException {
    public UserNotFoundException() {
        super("Account not found.");
    }
}
