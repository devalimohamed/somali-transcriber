package com.somtranscriber;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.somtranscriber.auth.dto.*;
import com.somtranscriber.auth.model.*;
import com.somtranscriber.calls.dto.*;
import com.somtranscriber.calls.model.*;
import com.somtranscriber.common.security.AuthenticatedUser;
import com.somtranscriber.common.security.SecurityUtils;
import com.somtranscriber.common.util.Hashing;
import com.somtranscriber.common.web.ErrorResponse;
import com.somtranscriber.config.AppProperties;
import com.somtranscriber.processing.adapter.OllamaFormatterAdapter;
import com.somtranscriber.processing.adapter.OpenAiTranslationAdapter;
import com.somtranscriber.processing.adapter.OpenAiTranscriptionAdapter;
import com.somtranscriber.processing.model.JobAttemptEntity;
import com.somtranscriber.processing.model.JobStage;
import com.somtranscriber.processing.service.TranscriptionResult;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.client.RestClient;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class ModelCoverageTest {

    @Test
    void modelAndDtoAccessorsWork() {
        Instant now = Instant.now();
        UUID id = UUID.randomUUID();

        UserEntity user = new UserEntity();
        user.setId(id);
        user.setEmail("USER@EXAMPLE.COM");
        user.setPasswordHash("hash");
        user.setRole(UserRole.WORKER);
        user.setStatus(UserStatus.ACTIVE);
        user.setCreatedAt(now);
        assertThat(user.getEmail()).isEqualTo("user@example.com");
        assertThat(user.getRole()).isEqualTo(UserRole.WORKER);

        InviteEntity invite = new InviteEntity();
        invite.setId(id);
        invite.setEmail("INVITE@EXAMPLE.COM");
        invite.setTokenHash("token-hash");
        invite.setExpiresAt(now);
        invite.setAcceptedAt(now);
        invite.setCreatedBy(id);
        invite.setCreatedAt(now);
        assertThat(invite.getEmail()).isEqualTo("invite@example.com");
        assertThat(invite.getCreatedBy()).isEqualTo(id);

        RefreshTokenEntity refreshToken = new RefreshTokenEntity();
        refreshToken.setId(id);
        refreshToken.setTokenId("jti");
        refreshToken.setTokenHash("hash");
        refreshToken.setUserId(id);
        refreshToken.setExpiresAt(now);
        refreshToken.setRevokedAt(now);
        refreshToken.setCreatedAt(now);
        assertThat(refreshToken.getTokenId()).isEqualTo("jti");

        CallRecordEntity callRecord = new CallRecordEntity();
        callRecord.setId(id);
        callRecord.setUserId(id);
        callRecord.setCallAt(now);
        callRecord.setStatus(CallStatus.CREATED);
        callRecord.setAudioObjectKey("audio-key");
        callRecord.setDetectedLanguage("so");
        callRecord.setTranscriptEnglish("English");
        callRecord.setTranscriptModel("gpt-4o-transcribe");
        callRecord.setTranscriptLatencyMs(10L);
        callRecord.setNoteText("note");
        callRecord.setNoteSource(NoteSource.FORMATTER);
        callRecord.setWarning("none");
        callRecord.setFinalText("final");
        callRecord.setFinalizedAt(now);
        callRecord.setCreatedAt(now);
        callRecord.setUpdatedAt(now);
        assertThat(callRecord.getNoteText()).isEqualTo("note");
        assertThat(callRecord.getFinalText()).isEqualTo("final");

        JobAttemptEntity attempt = new JobAttemptEntity();
        attempt.setId(id);
        attempt.setCallId(id);
        attempt.setStage(JobStage.FORMATTER);
        attempt.setAttemptNo(2);
        attempt.setErrorCode("ERR");
        attempt.setNextRetryAt(now);
        attempt.setCreatedAt(now);
        assertThat(attempt.getAttemptNo()).isEqualTo(2);

        LoginRequest loginRequest = new LoginRequest("worker@example.com", "123456");
        RefreshRequest refreshRequest = new RefreshRequest("refresh");
        LogoutRequest logoutRequest = new LogoutRequest("refresh");
        TokenResponse tokenResponse = new TokenResponse("a", now, "r", now);
        CreateInviteRequest createInviteRequest = new CreateInviteRequest("user@example.com", 24);
        CreateInviteResponse createInviteResponse = new CreateInviteResponse(id, "token", now);
        AcceptInviteRequest acceptInviteRequest = new AcceptInviteRequest("token", "123456");
        CreateCallRequest createCallRequest = new CreateCallRequest(now);
        UpdateDraftRequest updateDraftRequest = new UpdateDraftRequest("Note body");
        CallMetadataResponse metadataResponse = new CallMetadataResponse(now, id);
        CallResponse callResponse = new CallResponse(id, CallStatus.READY, "note", null, metadataResponse, false, now, now);

        assertThat(loginRequest.username()).isEqualTo("worker@example.com");
        assertThat(refreshRequest.refreshToken()).isEqualTo("refresh");
        assertThat(logoutRequest.refreshToken()).isEqualTo("refresh");
        assertThat(tokenResponse.accessToken()).isEqualTo("a");
        assertThat(createInviteRequest.email()).isEqualTo("user@example.com");
        assertThat(createInviteResponse.inviteToken()).isEqualTo("token");
        assertThat(loginRequest.pin()).isEqualTo("123456");
        assertThat(acceptInviteRequest.pin()).isEqualTo("123456");
        assertThat(createCallRequest.callAt()).isEqualTo(now);
        assertThat(updateDraftRequest.noteText()).isEqualTo("Note body");
        assertThat(callResponse.metadata().callAt()).isEqualTo(now);
        assertThat(CallStatus.valueOf("READY")).isEqualTo(CallStatus.READY);
        assertThat(NoteSource.valueOf("FORMATTER")).isEqualTo(NoteSource.FORMATTER);
        assertThat(JobStage.valueOf("TRANSCRIPTION")).isEqualTo(JobStage.TRANSCRIPTION);

        ErrorResponse errorResponse = new ErrorResponse(now, 400, "Bad Request", "message", "/path");
        assertThat(errorResponse.status()).isEqualTo(400);

        String hash = Hashing.sha256Hex("hello");
        assertThat(hash).hasSize(64);

        AuthenticatedUser principal = new AuthenticatedUser(id, "worker@example.com", UserRole.WORKER);
        UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(principal, null, List.of());
        SecurityContextHolder.getContext().setAuthentication(auth);
        assertThat(SecurityUtils.currentUser().email()).isEqualTo("worker@example.com");
        SecurityContextHolder.clearContext();
    }

    @Test
    void adaptersHandleLocalFallbackMode() throws Exception {
        AppProperties properties = new AppProperties(
                new AppProperties.Jwt("issuer", 15, 30, "super-secret-super-secret-super-secret"),
                new AppProperties.Audio("/tmp/test-audio", 120),
                new AppProperties.OpenAi("", "gpt-4o-transcribe", "gpt-4o-mini", "https://api.openai.com"),
                new AppProperties.Ollama("", "qwen2.5:3b"),
                new AppProperties.Retry("queue", 3, false),
                new AppProperties.Cors(List.of("http://localhost"))
        );

        OpenAiTranscriptionAdapter transcriptionAdapter = new OpenAiTranscriptionAdapter(RestClient.builder(), new ObjectMapper(), properties);
        Path audio = Files.createTempFile("audio", ".m4a");
        Files.writeString(audio, "data");
        TranscriptionResult result = transcriptionAdapter.transcribe(audio, "audio/mpeg");
        assertThat(result.englishText()).contains("fallback");

        OllamaFormatterAdapter formatterAdapter = new OllamaFormatterAdapter(RestClient.builder(), new ObjectMapper(), properties);
        assertThat(formatterAdapter.format("Raw summary text")).isEqualTo("Raw summary text");

        OpenAiTranslationAdapter translationAdapter = new OpenAiTranslationAdapter(RestClient.builder(), new ObjectMapper(), properties);
        assertThat(translationAdapter.translateToEnglish("Waxaan la hadlay klinik", "so"))
                .isEqualTo("Waxaan la hadlay klinik");
        Files.deleteIfExists(audio);
    }
}
