package com.smarthelp.model;

import java.time.LocalDateTime;

public record User(
        Long id,
        String name,
        String email,
        String role,
        LocalDateTime createdAt) {
}
