package com.jrl.auth.entity.enums;

/** Must match com.rlaas.ratelimiter.Entity.Enums.IdentifierSource in the rate limiter proxy exactly. */
public enum IdentifierSource {
    HEADER, QUERY_PARAM, COOKIE, IP, API_KEY, PATH_VARIABLE
}
