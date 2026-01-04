package com.somtranscriber.processing.service;

import com.somtranscriber.processing.model.JobStage;

import java.time.Instant;
import java.util.UUID;

public record RetryJob(
        UUID callId,
        JobStage stage,
        int attempt,
        Instant availableAt
) {
}
