package com.somtranscriber.calls.dto;

import com.somtranscriber.calls.model.CallStatus;

import java.time.Instant;
import java.util.UUID;

public record CallResponse(
        UUID callId,
        CallStatus status,
        String noteText,
        String warning,
        CallMetadataResponse metadata,
        boolean isFinal,
        Instant createdAt,
        Instant updatedAt
) {
}
