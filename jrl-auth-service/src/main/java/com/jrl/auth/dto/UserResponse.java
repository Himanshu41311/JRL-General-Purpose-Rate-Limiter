package com.jrl.auth.dto;

import com.jrl.auth.entity.User;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class UserResponse {

    private String id;
    private String customerId;
    private String name;
    private String email;

    public static UserResponse from(User user) {
        return UserResponse.builder()
                .id(user.getId().toString())
                .customerId(user.getCustomerId().toString())
                .name(user.getName())
                .email(user.getEmail())
                .build();
    }
}
