
package com.shrtrl.DTOs;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import org.hibernate.validator.constraints.Length;

public record LoginRequest(
        @NotBlank(message = "email is required")
        @Email(message = "email must be valid")
        @Length(min=15, max=30)
        String email,

        @NotBlank(message = "password is required")
        @Length(min=8)
        String password
) {
}
