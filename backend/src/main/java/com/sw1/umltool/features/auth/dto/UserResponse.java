package com.sw1.umltool.features.auth.dto;

import com.sw1.umltool.features.auth.model.UserEntity;
import lombok.Builder;
import lombok.Value;

@Value @Builder
public class UserResponse {
    String id;
    String firstName;
    String lastName;
    String email;
    String theme;

    public static UserResponse from(UserEntity user) {
        return builder().id(user.getId()).firstName(user.getFirstName()).lastName(user.getLastName()).email(user.getEmail()).theme(user.getTheme() == null ? "LAVENDER" : user.getTheme()).build();
    }
}
