package com.somtranscriber.calls.service;

import com.somtranscriber.calls.dto.CallMetadataResponse;
import com.somtranscriber.calls.dto.CallResponse;
import com.somtranscriber.calls.model.CallRecordEntity;

public final class CallMapper {

    private CallMapper() {
    }

    public static CallResponse toResponse(CallRecordEntity entity) {
        String noteText = entity.getFinalText() != null ? entity.getFinalText() : entity.getNoteText();
        return new CallResponse(
                entity.getId(),
                entity.getStatus(),
                noteText,
                entity.getWarning(),
                new CallMetadataResponse(entity.getCallAt(), entity.getUserId()),
                entity.getFinalText() != null,
                entity.getCreatedAt(),
                entity.getUpdatedAt()
        );
    }
}
