package com.sw1.umltool.features.auth.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class UpdateThemeRequest {
    @NotBlank private String theme;
}
