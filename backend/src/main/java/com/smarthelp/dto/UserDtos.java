package com.smarthelp.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public final class UserDtos {

    private UserDtos() {
    }

    public record CreateUserRequest(
            @NotBlank @Size(max = 100) String name,
            @NotBlank @Email @Size(max = 255) String email,
            @NotBlank @Pattern(regexp = "CUSTOMER|AGENT") String role) {
    }

    public record UpdateUserRequest(
            @NotBlank @Size(max = 100) String name,
            @NotBlank @Email @Size(max = 255) String email,
            @NotBlank @Pattern(regexp = "CUSTOMER|AGENT") String role) {
    }
}
