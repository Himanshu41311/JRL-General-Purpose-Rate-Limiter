package com.jrl.auth.exception;

public class RouteNotFoundException extends RuntimeException {
    public RouteNotFoundException() {
        super("Route not found.");
    }
}
