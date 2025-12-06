package com.somtranscriber.auth.dto;

import java.time.Instant;
import java.util.UUID;

public record CreateInviteResponse(
        UUID inviteId,
        String inviteToken,
        Instant expiresAt
) {
}
