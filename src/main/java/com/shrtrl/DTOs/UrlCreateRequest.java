package com.shrtrl.DTOs;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.Positive;
import org.hibernate.validator.constraints.URL;

public record UrlCreateRequest(
        @NotBlank(message = "longUrl is required")
        @Size(max = 20, message = "longUrl cannot exceed 20 characters")
        @URL(message = "longUrl must be a valid URL")
        @Pattern(
                regexp = "(?i)^https?://.*",
                message = "longUrl must use http or https"
        )
        String longUrl,

        @Positive(message = "groupId must be positive")
        Integer groupId
) {
    public UrlCreateRequest(String longUrl) {
        this(longUrl, null);
    }
}
