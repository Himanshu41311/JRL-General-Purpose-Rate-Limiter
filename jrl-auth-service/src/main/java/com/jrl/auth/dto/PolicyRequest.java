package com.jrl.auth.dto;

import com.jrl.auth.entity.enums.IdentifierSource;
import com.jrl.auth.entity.enums.RateLimitAlgorithm;
import com.jrl.auth.entity.enums.Scope;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class PolicyRequest {

    @NotNull(message = "Scope is required")
    private Scope scope;

    // Required only when scope is USER or API_KEY — checked in the service,
    // not here, since the rule depends on another field's value.
    private IdentifierSource identifierSource;
    private String identifierValue;

    @NotNull(message = "Algorithm is required")
    private RateLimitAlgorithm algorithm;

    @NotBlank(message = "algorithmConfig is required")
    private String algorithmConfig;

    // Only read on update.
    private Boolean active;
}
