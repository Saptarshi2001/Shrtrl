package com.shrtrl.DTOs;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.hibernate.validator.constraints.URL;
import java.time.OffsetDateTime;
import jakarta.validation.constraints.NotNull;

public record UrlCreateResponse(
        @URL
        @NotBlank
        @Size(min = 8, max = 2048)
        @Pattern(regexp = "(?i)^https?://[^\\s\\p{Cntrl}]+$")
        String longUrl,

        @NotBlank
        @Size(min = 1, max = 32)
        @Pattern(regexp = "^[a-zA-Z0-9]+$")
        String shortCode,

        @NotNull
        OffsetDateTime createdDate,
        boolean result
) {}
