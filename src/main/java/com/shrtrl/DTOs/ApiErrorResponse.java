package com.shrtrl.DTOs;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Max;

public record ApiErrorResponse(
        @Min(400)
        @Max(599)
        int statusCode,

        @NotBlank
        @Size(min = 1, max = 100)
        @Pattern(regexp = "^[a-zA-Z0-9 -]+$")
        String errorType,

        @NotBlank
        @Size(min = 1, max = 255)
        @Pattern(regexp = "^[a-zA-Z0-9 ]+$")
        String errmsg
) {}
