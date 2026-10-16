package com.shrtrl.DTOs;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record GroupCreateResponse(
        @NotBlank
        @Size(min = 5, max = 15)
        @Pattern(regexp = "^[a-zA-Z0-9 ]+$")
        String groupName,

        @NotBlank
        @Size(min = 1, max = 255)
        @Pattern(regexp = "^[a-zA-Z0-9 ]+$")
        String msg
) {}
