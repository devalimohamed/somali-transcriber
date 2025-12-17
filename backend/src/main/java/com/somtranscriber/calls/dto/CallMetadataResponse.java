package com.somtranscriber.calls.dto;

import java.time.Instant;
import java.util.UUID;

public record CallMetadataResponse(
        Instant callAt,
        UUID userId
) {
}
