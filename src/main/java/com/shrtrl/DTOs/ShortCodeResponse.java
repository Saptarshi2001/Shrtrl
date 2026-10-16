package com.shrtrl.DTOs;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.hibernate.validator.constraints.URL;

public record ShortCodeResponse(
        @URL
        @NotBlank
        @Size(min = 8, max = 2048)
        @Pattern(regexp = "(?i)^https?://[^\\s\\p{Cntrl}]+$")
        String LongUrl,

        @NotBlank
        @Size(min = 1, max = 32)
        @Pattern(regexp = "^[a-zA-Z0-9]+$")
        String shortCode,

        @NotBlank
        @Size(min = 1, max = 255)
        @Pattern(regexp = "^[a-zA-Z0-9 ]+$")
        String msg
) {}
