package com.doodle.mini.api.user;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateUserRequest(
    @NotBlank
    @Size(max = 120)
    String name,

    @NotBlank
    @Email
    @Size(max = 255)
    String email,

    @Size(max = 64)
    String timezone) {
}
