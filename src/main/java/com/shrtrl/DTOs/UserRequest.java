package com.shrtrl.DTOs;
import jakarta.validation.constraints.NotBlank;
import org.hibernate.validator.constraints.Length;
import jakarta.validation.constraints.Email;

public record UserRequest(
        @NotBlank(message = "username should not be blank")
        @Length(min=5, max=20)
        String username,

        @Email
        String email
) {
}
