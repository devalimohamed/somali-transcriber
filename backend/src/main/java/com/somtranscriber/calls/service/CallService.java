package com.somtranscriber.calls.service;

import com.somtranscriber.calls.dto.CreateCallRequest;
import com.somtranscriber.calls.dto.UpdateDraftRequest;
import com.somtranscriber.calls.model.CallRecordEntity;
import com.somtranscriber.calls.model.CallStatus;
import com.somtranscriber.calls.repo.CallRecordRepository;
import com.somtranscriber.common.exception.BadRequestException;
import com.somtranscriber.common.exception.NotFoundException;
import com.somtranscriber.processing.service.ProcessingService;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
public class CallService {

    private final CallRecordRepository callRecordRepository;
    private final ProcessingService processingService;
    private final MeterRegistry meterRegistry;

    public CallService(CallRecordRepository callRecordRepository,
                       ProcessingService processingService,
                       MeterRegistry meterRegistry) {
        this.callRecordRepository = callRecordRepository;
        this.processingService = processingService;
        this.meterRegistry = meterRegistry;
    }

    @Transactional
    public CallRecordEntity createCall(UUID userId, CreateCallRequest request) {
        CallRecordEntity entity = new CallRecordEntity();
        entity.setUserId(userId);
        entity.setCallAt(request.callAt());
        entity.setStatus(CallStatus.CREATED);
        return callRecordRepository.save(entity);
    }

    @Transactional
    public CallRecordEntity uploadAudio(UUID userId, UUID callId, MultipartFile file, int durationSeconds) {
        CallRecordEntity call = getOwnedCall(callId, userId);
        if (call.getStatus() == CallStatus.FINALIZED) {
            throw new BadRequestException("Cannot upload audio for finalized note");
        }

        return processingService.processUpload(call, file, durationSeconds);
    }

    @Transactional(readOnly = true)
    public CallRecordEntity getCall(UUID callId, UUID userId) {
        return getOwnedCall(callId, userId);
    }

    @Transactional
    public CallRecordEntity updateDraft(UUID callId, UUID userId, UpdateDraftRequest request) {
        CallRecordEntity call = getOwnedCall(callId, userId);
        if (call.getStatus() == CallStatus.FINALIZED) {
            throw new BadRequestException("Finalized note cannot be edited");
        }
        if (call.getStatus() == CallStatus.CREATED || call.getStatus() == CallStatus.UPLOADED || call.getStatus() == CallStatus.TRANSCRIBING || call.getStatus() == CallStatus.FORMATTING) {
            throw new BadRequestException("Draft is not ready yet");
        }

        call.setNoteText(request.noteText().trim());
        return callRecordRepository.save(call);
    }

    @Transactional
    public CallRecordEntity finalizeCall(UUID callId, UUID userId) {
        CallRecordEntity call = getOwnedCall(callId, userId);
        if (call.getStatus() == CallStatus.FINALIZED) {
            return call;
        }
        if (call.getNoteText() == null || call.getNoteText().isBlank()) {
            throw new BadRequestException("Cannot finalize empty draft");
        }

        call.setFinalText(call.getNoteText());
        call.setFinalizedAt(Instant.now());
        call.setStatus(CallStatus.FINALIZED);

        meterRegistry.counter("calls.finalized.total").increment();
        return callRecordRepository.save(call);
    }

    @Transactional(readOnly = true)
    public List<CallRecordEntity> listCalls(UUID userId, Instant from, Instant to) {
        if (from != null && to != null) {
            return callRecordRepository.findByUserIdAndCallAtBetweenOrderByCallAtDesc(userId, from, to);
        }
        return callRecordRepository.findByUserIdOrderByCallAtDesc(userId);
    }

    private CallRecordEntity getOwnedCall(UUID callId, UUID userId) {
        return callRecordRepository.findByIdAndUserId(callId, userId)
                .orElseThrow(() -> new NotFoundException("Call record not found"));
    }
}
