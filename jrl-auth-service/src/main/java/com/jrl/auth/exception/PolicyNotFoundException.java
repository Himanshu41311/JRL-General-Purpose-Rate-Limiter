package com.jrl.auth.exception;

public class PolicyNotFoundException extends RuntimeException {
    public PolicyNotFoundException() {
        super("Policy not found.");
    }
}
