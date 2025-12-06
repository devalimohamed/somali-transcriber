package com.somtranscriber.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record CreateInviteRequest(
        @Email @NotBlank String email,
        Integer expiresInHours
) {
}
