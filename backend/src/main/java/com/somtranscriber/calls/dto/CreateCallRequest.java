package com.somtranscriber.calls.dto;

import jakarta.validation.constraints.NotNull;

import java.time.Instant;

public record CreateCallRequest(
        @NotNull Instant callAt
) {
}
