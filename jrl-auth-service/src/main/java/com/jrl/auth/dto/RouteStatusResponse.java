package com.jrl.auth.dto;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class RouteStatusResponse {

    private String routeId;
    private boolean live;
    private String checkedAt;
}
