

package com.shrtrl.DTOs;

import jakarta.validation.constraints.NotBlank;
import org.hibernate.validator.constraints.Length;

public record UpdateGroup(
        @NotBlank(message = "group name is required")
        @Length(min=5, max=15)
        String oldgroupName,

        @NotBlank(message = "group name is required")
        @Length(min=5, max=15)
        String newgroupname
) {}
