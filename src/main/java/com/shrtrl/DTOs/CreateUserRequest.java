package com.shrtrl.DTOs;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import org.hibernate.validator.constraints.Length;

public record CreateUserRequest(
        @NotBlank(message = "username is required")
        @Length(min=5, max=20)
        @Pattern(regexp = "^[a-zA-Z]+$", message = "Numbers and special characters are not allowed")
        String username,

        @NotBlank(message = "email is required")
        @Email(message = "email must be valid")
        @Length(min=15, max=30)
        String email,

        @NotBlank(message = "password is required")
        @Length(min=8)
        String password
) {
}
