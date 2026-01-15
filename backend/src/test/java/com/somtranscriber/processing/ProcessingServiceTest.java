package com.somtranscriber.processing;

import com.somtranscriber.calls.model.CallRecordEntity;
import com.somtranscriber.calls.model.CallStatus;
import com.somtranscriber.calls.model.NoteSource;
import com.somtranscriber.calls.repo.CallRecordRepository;
import com.somtranscriber.config.AppProperties;
import com.somtranscriber.processing.model.JobAttemptEntity;
import com.somtranscriber.processing.model.JobStage;
import com.somtranscriber.processing.repo.JobAttemptRepository;
import com.somtranscriber.processing.service.*;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ProcessingServiceTest {

    @Mock
    private CallRecordRepository callRecordRepository;

    @Mock
    private JobAttemptRepository jobAttemptRepository;

    @Mock
    private TranscriptionAdapter transcriptionAdapter;

    @Mock
    private TranslationAdapter translationAdapter;

    @Mock
    private FormatterAdapter formatterAdapter;

    @Mock
    private AudioStorageService audioStorageService;

    @Mock
    private RetryQueueService retryQueueService;

    private Path audioPath;

    @AfterEach
    void cleanup() throws Exception {
        if (audioPath != null) {
            Files.deleteIfExists(audioPath);
        }
    }

    @Test
    void processUploadSuccessPath() throws Exception {
        ProcessingService service = createService(3);
        CallRecordEntity call = baseCall();
        MockMultipartFile file = new MockMultipartFile("file", "clip.m4a", "audio/mpeg", "abc".getBytes());

        audioPath = Files.createTempFile("clip", ".m4a");
        Files.writeString(audioPath, "audio");

        when(audioStorageService.store(any())).thenReturn("audio-key");
        when(audioStorageService.resolve("audio-key")).thenReturn(audioPath);
        when(callRecordRepository.save(any(CallRecordEntity.class))).thenAnswer(inv -> inv.getArgument(0));
        when(callRecordRepository.findById(call.getId())).thenReturn(Optional.of(call));
        when(transcriptionAdapter.transcribe(any(Path.class), anyString()))
                .thenReturn(new TranscriptionResult("so", "translated text", "gpt-4o-transcribe", 100));
        when(translationAdapter.translateToEnglish("translated text", "so")).thenReturn("translated text");
        when(formatterAdapter.format("translated text")).thenReturn("Formatted summary");

        CallRecordEntity result = service.processUpload(call, file, 45);

        assertThat(result.getStatus()).isEqualTo(CallStatus.READY);
        assertThat(result.getNoteText()).isEqualTo("Formatted summary");
        assertThat(result.getNoteSource()).isEqualTo(NoteSource.FORMATTER);
        assertThat(result.getAudioObjectKey()).isNull();
        verify(audioStorageService).delete("audio-key");
    }

    @Test
    void processUploadFallsBackWhenFormatterFails() throws Exception {
        ProcessingService service = createService(3);
        CallRecordEntity call = baseCall();
        MockMultipartFile file = new MockMultipartFile("file", "clip.m4a", "audio/mpeg", "abc".getBytes());

        audioPath = Files.createTempFile("clip", ".m4a");
        Files.writeString(audioPath, "audio");

        when(audioStorageService.store(any())).thenReturn("audio-key");
        when(audioStorageService.resolve("audio-key")).thenReturn(audioPath);
        when(callRecordRepository.save(any(CallRecordEntity.class))).thenAnswer(inv -> inv.getArgument(0));
        when(callRecordRepository.findById(call.getId())).thenReturn(Optional.of(call));
        when(transcriptionAdapter.transcribe(any(Path.class), anyString()))
                .thenReturn(new TranscriptionResult("so", "translated text", "gpt-4o-transcribe", 50));
        when(translationAdapter.translateToEnglish("translated text", "so")).thenReturn("translated text");
        when(formatterAdapter.format(anyString())).thenThrow(new IllegalStateException("formatter down"));
        when(jobAttemptRepository.findTopByCallIdAndStageOrderByAttemptNoDesc(any(), eq(JobStage.FORMATTER)))
                .thenReturn(Optional.empty());
        when(jobAttemptRepository.save(any(JobAttemptEntity.class))).thenAnswer(inv -> inv.getArgument(0));

        CallRecordEntity result = service.processUpload(call, file, 40);

        assertThat(result.getStatus()).isEqualTo(CallStatus.READY_WITH_WARNING);
        assertThat(result.getNoteText()).isEqualTo("translated text");
        assertThat(result.getNoteSource()).isEqualTo(NoteSource.RAW_TRANSLATION);
        verify(retryQueueService).enqueue(any(RetryJob.class));
        verify(audioStorageService).delete("audio-key");
    }

    @Test
    void processUploadUsesRawTranslationWhenFormatterAddsUnsupportedDetails() throws Exception {
        ProcessingService service = createService(3);
        CallRecordEntity call = baseCall();
        MockMultipartFile file = new MockMultipartFile("file", "clip.m4a", "audio/mpeg", "abc".getBytes());

        audioPath = Files.createTempFile("clip", ".m4a");
        Files.writeString(audioPath, "audio");

        String transcript = "Called patient family and confirmed medicine pickup tomorrow.";
        when(audioStorageService.store(any())).thenReturn("audio-key");
        when(audioStorageService.resolve("audio-key")).thenReturn(audioPath);
        when(callRecordRepository.save(any(CallRecordEntity.class))).thenAnswer(inv -> inv.getArgument(0));
        when(callRecordRepository.findById(call.getId())).thenReturn(Optional.of(call));
        when(transcriptionAdapter.transcribe(any(Path.class), anyString()))
                .thenReturn(new TranscriptionResult("so", transcript, "gpt-4o-transcribe", 40));
        when(translationAdapter.translateToEnglish(transcript, "so")).thenReturn(transcript);
        when(formatterAdapter.format(transcript))
                .thenReturn("Met with the team and prepared the weekly report for stakeholders.");

        CallRecordEntity result = service.processUpload(call, file, 30);

        assertThat(result.getStatus()).isEqualTo(CallStatus.READY_WITH_WARNING);
        assertThat(result.getNoteText()).isEqualTo(transcript);
        assertThat(result.getNoteSource()).isEqualTo(NoteSource.RAW_TRANSLATION);
        assertThat(result.getWarning()).contains("inaccurate");
        verify(audioStorageService).delete("audio-key");
    }

    @Test
    void processUploadSchedulesRetryOnTranscriptionFailure() throws Exception {
        ProcessingService service = createService(3);
        CallRecordEntity call = baseCall();
        MockMultipartFile file = new MockMultipartFile("file", "clip.m4a", "audio/mpeg", "abc".getBytes());

        audioPath = Files.createTempFile("clip", ".m4a");
        Files.writeString(audioPath, "audio");

        when(audioStorageService.store(any())).thenReturn("audio-key");
        when(audioStorageService.resolve("audio-key")).thenReturn(audioPath);
        when(callRecordRepository.save(any(CallRecordEntity.class))).thenAnswer(inv -> inv.getArgument(0));
        when(callRecordRepository.findById(call.getId())).thenReturn(Optional.of(call));
        when(transcriptionAdapter.transcribe(any(Path.class), anyString())).thenThrow(new IllegalStateException("openai timeout"));
        when(jobAttemptRepository.findTopByCallIdAndStageOrderByAttemptNoDesc(any(), eq(JobStage.TRANSCRIPTION)))
                .thenReturn(Optional.empty());
        when(jobAttemptRepository.save(any(JobAttemptEntity.class))).thenAnswer(inv -> inv.getArgument(0));

        CallRecordEntity result = service.processUpload(call, file, 50);

        assertThat(result.getStatus()).isEqualTo(CallStatus.FAILED);
        assertThat(result.getWarning()).contains("retry");
        assertThat(result.getAudioObjectKey()).isEqualTo("audio-key");
        verify(retryQueueService).enqueue(any(RetryJob.class));
        verify(audioStorageService, never()).delete(anyString());
    }

    @Test
    void processUploadSchedulesRetryOnTranslationFailure() throws Exception {
        ProcessingService service = createService(3);
        CallRecordEntity call = baseCall();
        MockMultipartFile file = new MockMultipartFile("file", "clip.m4a", "audio/mpeg", "abc".getBytes());

        audioPath = Files.createTempFile("clip", ".m4a");
        Files.writeString(audioPath, "audio");

        when(audioStorageService.store(any())).thenReturn("audio-key");
        when(audioStorageService.resolve("audio-key")).thenReturn(audioPath);
        when(callRecordRepository.save(any(CallRecordEntity.class))).thenAnswer(inv -> inv.getArgument(0));
        when(callRecordRepository.findById(call.getId())).thenReturn(Optional.of(call));
        when(transcriptionAdapter.transcribe(any(Path.class), anyString()))
                .thenReturn(new TranscriptionResult("so", "qoraal", "gpt-4o-transcribe", 70));
        when(translationAdapter.translateToEnglish("qoraal", "so"))
                .thenThrow(new IllegalStateException("translation unavailable"));
        when(jobAttemptRepository.findTopByCallIdAndStageOrderByAttemptNoDesc(any(), eq(JobStage.TRANSCRIPTION)))
                .thenReturn(Optional.empty());
        when(jobAttemptRepository.save(any(JobAttemptEntity.class))).thenAnswer(inv -> inv.getArgument(0));

        CallRecordEntity result = service.processUpload(call, file, 50);

        assertThat(result.getStatus()).isEqualTo(CallStatus.FAILED);
        assertThat(result.getWarning()).contains("retry");
        verify(retryQueueService).enqueue(any(RetryJob.class));
    }

    @Test
    void processRetryFormatterCanRecoverWarningState() {
        ProcessingService service = createService(3);
        CallRecordEntity call = baseCall();
        call.setStatus(CallStatus.READY_WITH_WARNING);
        call.setTranscriptEnglish("raw transcript");
        call.setNoteText("raw transcript");
        call.setNoteSource(NoteSource.RAW_TRANSLATION);

        when(callRecordRepository.findById(call.getId())).thenReturn(Optional.of(call));
        when(callRecordRepository.save(any(CallRecordEntity.class))).thenAnswer(inv -> inv.getArgument(0));
        when(formatterAdapter.format("raw transcript")).thenReturn("cleaned summary");

        service.processRetryJob(new RetryJob(call.getId(), JobStage.FORMATTER, 2, Instant.now()));

        assertThat(call.getStatus()).isEqualTo(CallStatus.READY);
        assertThat(call.getNoteText()).isEqualTo("cleaned summary");
        assertThat(call.getWarning()).isNull();
    }

    @Test
    void processUploadDeletesAudioAfterTerminalTranscriptionFailure() throws Exception {
        ProcessingService service = createService(1);
        CallRecordEntity call = baseCall();
        MockMultipartFile file = new MockMultipartFile("file", "clip.m4a", "audio/mpeg", "abc".getBytes());

        audioPath = Files.createTempFile("clip", ".m4a");
        Files.writeString(audioPath, "audio");

        when(audioStorageService.store(any())).thenReturn("audio-key");
        when(audioStorageService.resolve("audio-key")).thenReturn(audioPath);
        when(callRecordRepository.save(any(CallRecordEntity.class))).thenAnswer(inv -> inv.getArgument(0));
        when(callRecordRepository.findById(call.getId())).thenReturn(Optional.of(call));
        when(transcriptionAdapter.transcribe(any(Path.class), anyString())).thenThrow(new IllegalStateException("down"));
        when(jobAttemptRepository.findTopByCallIdAndStageOrderByAttemptNoDesc(any(), eq(JobStage.TRANSCRIPTION)))
                .thenReturn(Optional.empty());
        when(jobAttemptRepository.save(any(JobAttemptEntity.class))).thenAnswer(inv -> inv.getArgument(0));

        CallRecordEntity result = service.processUpload(call, file, 55);

        assertThat(result.getStatus()).isEqualTo(CallStatus.FAILED);
        assertThat(result.getWarning()).contains("re-upload");
        assertThat(result.getAudioObjectKey()).isNull();
        verify(audioStorageService).delete("audio-key");
    }

    @Test
    void processUploadRejectsInvalidDurationAndMimeType() {
        ProcessingService service = createService(3);
        CallRecordEntity call = baseCall();
        MockMultipartFile invalidMime = new MockMultipartFile("file", "clip.txt", "text/plain", "abc".getBytes());

        try {
            service.processUpload(call, invalidMime, 10);
        } catch (Exception ignored) {
            // expected
        }

        MockMultipartFile audio = new MockMultipartFile("file", "clip.m4a", "audio/mpeg", "abc".getBytes());
        try {
            service.processUpload(call, audio, 121);
        } catch (Exception ignored) {
            // expected
        }

        verify(audioStorageService, never()).store(any());
    }

    @Test
    void processUploadQueuesAsyncJobWhenEnabled() {
        ProcessingService service = createService(3, true);
        CallRecordEntity call = baseCall();
        MockMultipartFile file = new MockMultipartFile("file", "clip.m4a", "audio/mpeg", "abc".getBytes());

        when(audioStorageService.store(any())).thenReturn("audio-key");
        when(callRecordRepository.save(any(CallRecordEntity.class))).thenAnswer(inv -> inv.getArgument(0));

        CallRecordEntity result = service.processUpload(call, file, 20);

        assertThat(result.getStatus()).isEqualTo(CallStatus.UPLOADED);
        assertThat(result.getAudioObjectKey()).isEqualTo("audio-key");
        verify(retryQueueService).enqueue(any(RetryJob.class));
        verify(callRecordRepository, never()).findById(any());
    }

    private ProcessingService createService(int maxAttempts) {
        return createService(maxAttempts, false);
    }

    private ProcessingService createService(int maxAttempts, boolean asyncOnUpload) {
        AppProperties properties = new AppProperties(
                new AppProperties.Jwt("issuer", 15, 30, "secret-secret-secret-secret-secret-secret"),
                new AppProperties.Audio("/tmp/audio", 120),
                new AppProperties.OpenAi("", "gpt-4o-transcribe", "gpt-4o-mini", "https://api.openai.com"),
                new AppProperties.Ollama("", "qwen2.5:3b"),
                new AppProperties.Retry("queue", maxAttempts, asyncOnUpload),
                new AppProperties.Cors(java.util.List.of("http://localhost"))
        );

        return new ProcessingService(
                callRecordRepository,
                jobAttemptRepository,
                transcriptionAdapter,
                translationAdapter,
                formatterAdapter,
                audioStorageService,
                retryQueueService,
                properties,
                new SimpleMeterRegistry()
        );
    }

    private CallRecordEntity baseCall() {
        CallRecordEntity call = new CallRecordEntity();
        call.setId(UUID.randomUUID());
        call.setUserId(UUID.randomUUID());
        call.setCallAt(Instant.now());
        call.setStatus(CallStatus.CREATED);
        return call;
    }
}
