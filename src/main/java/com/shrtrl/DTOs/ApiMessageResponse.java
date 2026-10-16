package com.shrtrl.DTOs;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record ApiMessageResponse(
        @NotBlank
        @Size(min = 1, max = 255)
        @Pattern(regexp = "^[a-zA-Z0-9 ]+$")
        String message
) {}
