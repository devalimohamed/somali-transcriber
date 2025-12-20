package com.somtranscriber.common.security;

import com.somtranscriber.auth.model.UserRole;

import java.util.UUID;

public record AuthenticatedUser(
        UUID userId,
        String email,
        UserRole role
) {
}
