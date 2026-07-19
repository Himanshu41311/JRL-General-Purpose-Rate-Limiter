package com.jrl.auth.exception;

public class InvalidCredentialsException extends RuntimeException {
    public InvalidCredentialsException() {
        // Deliberately generic — never reveal whether the email or the
        // password was the wrong part, that's an account-enumeration leak.
        super("Email or password is incorrect.");
    }
}
