package com.jrl.auth.entity.enums;

/** Must match com.rlaas.ratelimiter.Entity.Enums.Scope in the rate limiter proxy exactly. */
public enum Scope {
    GLOBAL, USER, IP, API_KEY
}
