package com.sw1.umltool.features.auth.dto;

import lombok.Builder;
import lombok.Value;

@Value @Builder
public class AuthResponse {
    String token;
    UserResponse user;
}
