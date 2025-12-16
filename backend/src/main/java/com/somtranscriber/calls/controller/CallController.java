package com.somtranscriber.calls.controller;

import com.somtranscriber.calls.dto.CallResponse;
import com.somtranscriber.calls.dto.CreateCallRequest;
import com.somtranscriber.calls.dto.UpdateDraftRequest;
import com.somtranscriber.calls.service.CallMapper;
import com.somtranscriber.calls.service.CallService;
import com.somtranscriber.common.security.AuthenticatedUser;
import com.somtranscriber.common.security.SecurityUtils;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/v1/calls")
@Validated
public class CallController {

    private final CallService callService;

    public CallController(CallService callService) {
        this.callService = callService;
    }

    @PostMapping
    public CallResponse createCall(@RequestBody @Valid CreateCallRequest request) {
        AuthenticatedUser user = SecurityUtils.currentUser();
        return CallMapper.toResponse(callService.createCall(user.userId(), request));
    }

    @PostMapping(path = "/{callId}/audio", consumes = {"multipart/form-data"})
    public CallResponse uploadAudio(@PathVariable UUID callId,
                                    @RequestPart("file") MultipartFile file,
                                    @RequestParam("durationSeconds") @Min(1) @Max(120) int durationSeconds) {
        AuthenticatedUser user = SecurityUtils.currentUser();
        return CallMapper.toResponse(callService.uploadAudio(user.userId(), callId, file, durationSeconds));
    }

    @GetMapping("/{callId}")
    public CallResponse getCall(@PathVariable UUID callId) {
        AuthenticatedUser user = SecurityUtils.currentUser();
        return CallMapper.toResponse(callService.getCall(callId, user.userId()));
    }

    @PatchMapping("/{callId}/draft")
    public CallResponse updateDraft(@PathVariable UUID callId,
                                    @RequestBody @Valid UpdateDraftRequest request) {
        AuthenticatedUser user = SecurityUtils.currentUser();
        return CallMapper.toResponse(callService.updateDraft(callId, user.userId(), request));
    }

    @PostMapping("/{callId}/finalize")
    public CallResponse finalizeCall(@PathVariable UUID callId) {
        AuthenticatedUser user = SecurityUtils.currentUser();
        return CallMapper.toResponse(callService.finalizeCall(callId, user.userId()));
    }

    @GetMapping
    public List<CallResponse> listCalls(@RequestParam(value = "from", required = false) Instant from,
                                        @RequestParam(value = "to", required = false) Instant to) {
        AuthenticatedUser user = SecurityUtils.currentUser();
        return callService.listCalls(user.userId(), from, to)
                .stream()
                .map(CallMapper::toResponse)
                .toList();
    }
}
