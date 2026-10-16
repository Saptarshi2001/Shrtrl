package com.shrtrl.DTOs;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.Email;

public record UserProfileResponse(
        @NotBlank
        @Size(min = 5, max = 20)
        @Pattern(regexp = "^[a-zA-Z]+$")
        String userName,

        @Email
        @NotBlank
        @Size(min = 3, max = 255)
        @Pattern(regexp = "^[^\\s\\p{Cntrl}]+$")
        String email
) {}
