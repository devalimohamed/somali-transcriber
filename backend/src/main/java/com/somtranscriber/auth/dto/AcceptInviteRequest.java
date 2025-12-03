package com.somtranscriber.auth.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record AcceptInviteRequest(
        @NotBlank String inviteToken,
        @NotBlank
        @Pattern(regexp = "\\d{6}", message = "pin must be exactly 6 digits")
        @JsonAlias("password") String pin
) {
}
