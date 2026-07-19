package com.jrl.auth.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class RouteRequest {

    @NotBlank(message = "Name is required")
    private String name;

    @NotBlank(message = "Target URL is required")
    private String targetUrl;

    // Only read on update; a new route is always created active.
    private Boolean active;
}
