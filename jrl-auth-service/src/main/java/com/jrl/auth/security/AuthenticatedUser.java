package com.jrl.auth.security;

import java.util.UUID;

/**
 * What ends up in SecurityContextHolder's Authentication.getPrincipal()
 * once a JWT passes validation. Controllers pull this out via
 * @AuthenticationPrincipal instead of re-parsing the token themselves.
 */
public record AuthenticatedUser(UUID userId, String email, UUID customerId) {
}
