package com.somtranscriber.processing.service;

import com.somtranscriber.calls.model.CallRecordEntity;
import com.somtranscriber.calls.model.CallStatus;
import com.somtranscriber.calls.model.NoteSource;
import com.somtranscriber.calls.repo.CallRecordRepository;
import com.somtranscriber.common.exception.BadRequestException;
import com.somtranscriber.common.exception.NotFoundException;
import com.somtranscriber.config.AppProperties;
import com.somtranscriber.processing.model.JobAttemptEntity;
import com.somtranscriber.processing.model.JobStage;
import com.somtranscriber.processing.repo.JobAttemptRepository;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

@Service
public class ProcessingService {

    private static final Logger log = LoggerFactory.getLogger(ProcessingService.class);
    private static final Set<String> ALLOWED_MIME_TYPES = Set.of(
            "audio/mpeg",
            "audio/mp4",
            "audio/wav",
            "audio/x-wav",
            "audio/x-m4a",
            "audio/aac",
            "audio/ogg"
    );
    private static final Set<String> HIGH_RISK_ADDITIONS = Set.of(
            "meeting",
            "meetings",
            "report",
            "reports",
            "agenda",
            "stakeholder",
            "deadline",
            "presentation",
            "minutes",
            "action item",
            "project plan"
    );

    private final CallRecordRepository callRecordRepository;
    private final JobAttemptRepository jobAttemptRepository;
    private final TranscriptionAdapter transcriptionAdapter;
    private final TranslationAdapter translationAdapter;
    private final FormatterAdapter formatterAdapter;
    private final AudioStorageService audioStorageService;
    private final RetryQueueService retryQueueService;
    private final AppProperties appProperties;
    private final Timer transcriptionTimer;
    private final Timer translationTimer;
    private final Timer formatterTimer;
    private final Counter fallbackCounter;
    private final Counter retryCounter;

    public ProcessingService(CallRecordRepository callRecordRepository,
                             JobAttemptRepository jobAttemptRepository,
                             TranscriptionAdapter transcriptionAdapter,
                             TranslationAdapter translationAdapter,
                             FormatterAdapter formatterAdapter,
                             AudioStorageService audioStorageService,
                             RetryQueueService retryQueueService,
                             AppProperties appProperties,
                             MeterRegistry meterRegistry) {
        this.callRecordRepository = callRecordRepository;
        this.jobAttemptRepository = jobAttemptRepository;
        this.transcriptionAdapter = transcriptionAdapter;
        this.translationAdapter = translationAdapter;
        this.formatterAdapter = formatterAdapter;
        this.audioStorageService = audioStorageService;
        this.retryQueueService = retryQueueService;
        this.appProperties = appProperties;
        this.transcriptionTimer = meterRegistry.timer("calls.transcription.latency");
        this.translationTimer = meterRegistry.timer("calls.translation.latency");
        this.formatterTimer = meterRegistry.timer("calls.formatter.latency");
        this.fallbackCounter = meterRegistry.counter("calls.formatter.fallback.total");
        this.retryCounter = meterRegistry.counter("calls.retry.scheduled.total");
    }

    @Transactional
    public CallRecordEntity processUpload(CallRecordEntity call, MultipartFile file, int durationSeconds) {
        validateUpload(file, durationSeconds);

        String key = audioStorageService.store(file);
        call.setAudioObjectKey(key);
        call.setStatus(CallStatus.UPLOADED);
        call.setWarning(null);
        CallRecordEntity saved = callRecordRepository.save(call);

        if (!appProperties.retry().asyncOnUpload()) {
            return processTranscriptionAndFormatting(saved.getId(), true);
        }

        boolean scheduled = enqueueRetry(saved.getId(), JobStage.TRANSCRIPTION, 1, 1L);
        if (!scheduled) {
            return processTranscriptionAndFormatting(saved.getId(), true);
        }
        return saved;
    }

    @Transactional
    public CallRecordEntity processTranscriptionAndFormatting(UUID callId, boolean allowRetry) {
        CallRecordEntity call = callRecordRepository.findById(callId)
                .orElseThrow(() -> new NotFoundException("Call record not found"));

        if (call.getAudioObjectKey() == null || call.getAudioObjectKey().isBlank()) {
            throw new BadRequestException("No audio uploaded for this call");
        }

        Path audioPath = audioStorageService.resolve(call.getAudioObjectKey());
        if (!Files.exists(audioPath)) {
            throw new BadRequestException("Uploaded audio file is missing");
        }

        call.setStatus(CallStatus.TRANSCRIBING);
        callRecordRepository.save(call);

        TranscriptionResult transcription;
        String englishText;
        Timer.Sample transcriptionSample = Timer.start();
        try {
            transcription = transcriptionAdapter.transcribe(audioPath, "audio/*");
            Timer.Sample translationSample = Timer.start();
            try {
                englishText = translationAdapter.translateToEnglish(
                        transcription.englishText(),
                        transcription.detectedLanguage()
                );
            } finally {
                translationSample.stop(translationTimer);
            }

            if (englishText == null || englishText.isBlank()) {
                throw new IllegalStateException("Translation stage returned empty text");
            }
        } catch (Exception exception) {
            transcriptionSample.stop(transcriptionTimer);
            return handleTranscriptionFailure(call, exception, allowRetry);
        }
        transcriptionSample.stop(transcriptionTimer);

        call.setDetectedLanguage(transcription.detectedLanguage());
        call.setTranscriptEnglish(englishText);
        call.setTranscriptModel(transcription.providerModel());
        call.setTranscriptLatencyMs(transcription.latencyMs());

        call.setStatus(CallStatus.FORMATTING);
        callRecordRepository.save(call);

        Timer.Sample formattingSample = Timer.start();
        try {
            String formatted = formatterAdapter.format(englishText);
            if (looksUnfaithful(englishText, formatted)) {
                fallbackCounter.increment();
                call.setNoteText(englishText);
                call.setNoteSource(NoteSource.RAW_TRANSLATION);
                call.setStatus(CallStatus.READY_WITH_WARNING);
                call.setWarning("Formatter output looked inaccurate. Raw translation returned.");
            } else {
                call.setNoteText(formatted);
                call.setNoteSource(NoteSource.FORMATTER);
                call.setStatus(CallStatus.READY);
                call.setWarning(null);
            }
        } catch (Exception exception) {
            formattingSample.stop(formatterTimer);
            return handleFormatterFallback(call, exception);
        }
        formattingSample.stop(formatterTimer);

        CallRecordEntity saved = callRecordRepository.save(call);
        deleteAudio(saved);
        return saved;
    }

    @Transactional
    public void processRetryJob(RetryJob job) {
        CallRecordEntity call = callRecordRepository.findById(job.callId()).orElse(null);
        if (call == null || call.getStatus() == CallStatus.FINALIZED) {
            return;
        }

        if (job.stage() == JobStage.TRANSCRIPTION) {
            if (call.getAudioObjectKey() == null) {
                return;
            }
            processTranscriptionAndFormatting(call.getId(), true);
            return;
        }

        if (job.stage() == JobStage.FORMATTER) {
            retryFormatter(call);
        }
    }

    private CallRecordEntity handleTranscriptionFailure(CallRecordEntity call, Exception exception, boolean allowRetry) {
        int attempt = recordAttempt(call.getId(), JobStage.TRANSCRIPTION, exception.getClass().getSimpleName());
        boolean scheduled = false;

        if (allowRetry && attempt < appProperties.retry().maxAttempts()) {
            scheduled = enqueueRetry(call.getId(), JobStage.TRANSCRIPTION, attempt + 1, attempt * 15L);
        }

        call.setStatus(CallStatus.FAILED);
        call.setWarning(scheduled
                ? "Transcription failed. Automatic retry scheduled."
                : "Transcription failed. Please re-upload audio.");

        CallRecordEntity saved = callRecordRepository.save(call);
        if (!scheduled) {
            deleteAudio(saved);
        }
        return saved;
    }

    private CallRecordEntity handleFormatterFallback(CallRecordEntity call, Exception exception) {
        fallbackCounter.increment();
        int attempt = recordAttempt(call.getId(), JobStage.FORMATTER, exception.getClass().getSimpleName());

        boolean scheduled = false;
        if (attempt < appProperties.retry().maxAttempts()) {
            scheduled = enqueueRetry(call.getId(), JobStage.FORMATTER, attempt + 1, attempt * 10L);
        }

        call.setNoteText(call.getTranscriptEnglish());
        call.setNoteSource(NoteSource.RAW_TRANSLATION);
        call.setStatus(CallStatus.READY_WITH_WARNING);
        call.setWarning(scheduled
                ? "Formatter unavailable. Raw translation returned; retry scheduled."
                : "Formatter unavailable. Raw translation returned.");

        CallRecordEntity saved = callRecordRepository.save(call);
        deleteAudio(saved);
        return saved;
    }

    private void retryFormatter(CallRecordEntity call) {
        if (call.getTranscriptEnglish() == null || call.getTranscriptEnglish().isBlank()) {
            return;
        }
        if (call.getFinalText() != null) {
            return;
        }

        call.setStatus(CallStatus.FORMATTING);
        callRecordRepository.save(call);

        Timer.Sample sample = Timer.start();
        try {
            String formatted = formatterAdapter.format(call.getTranscriptEnglish());
            if (looksUnfaithful(call.getTranscriptEnglish(), formatted)) {
                call.setNoteText(call.getTranscriptEnglish());
                call.setNoteSource(NoteSource.RAW_TRANSLATION);
                call.setStatus(CallStatus.READY_WITH_WARNING);
                call.setWarning("Formatter output looked inaccurate. Using raw translation.");
            } else {
                call.setNoteText(formatted);
                call.setNoteSource(NoteSource.FORMATTER);
                call.setStatus(CallStatus.READY);
                call.setWarning(null);
            }
            callRecordRepository.save(call);
        } catch (Exception exception) {
            sample.stop(formatterTimer);
            int attempt = recordAttempt(call.getId(), JobStage.FORMATTER, exception.getClass().getSimpleName());
            if (attempt < appProperties.retry().maxAttempts()) {
                enqueueRetry(call.getId(), JobStage.FORMATTER, attempt + 1, attempt * 20L);
            }
            call.setStatus(CallStatus.READY_WITH_WARNING);
            call.setWarning("Formatter retry failed. Using raw translation.");
            callRecordRepository.save(call);
            return;
        }
        sample.stop(formatterTimer);
    }

    private boolean enqueueRetry(UUID callId, JobStage stage, int attempt, long delaySeconds) {
        try {
            retryQueueService.enqueue(new RetryJob(callId, stage, attempt, Instant.now().plusSeconds(delaySeconds)));
            retryCounter.increment();
            return true;
        } catch (Exception exception) {
            log.error("Unable to enqueue retry job for call {} stage {}", callId, stage, exception);
            return false;
        }
    }

    private int recordAttempt(UUID callId, JobStage stage, String errorCode) {
        int previousAttempt = jobAttemptRepository.findTopByCallIdAndStageOrderByAttemptNoDesc(callId, stage)
                .map(JobAttemptEntity::getAttemptNo)
                .orElse(0);
        int currentAttempt = previousAttempt + 1;

        JobAttemptEntity attempt = new JobAttemptEntity();
        attempt.setCallId(callId);
        attempt.setStage(stage);
        attempt.setAttemptNo(currentAttempt);
        attempt.setErrorCode(errorCode);
        attempt.setNextRetryAt(Instant.now().plusSeconds(currentAttempt * 10L));
        jobAttemptRepository.save(attempt);
        return currentAttempt;
    }

    private void validateUpload(MultipartFile file, int durationSeconds) {
        if (file == null || file.isEmpty()) {
            throw new BadRequestException("Audio file is required");
        }

        String contentType = file.getContentType();
        if (contentType == null || (!ALLOWED_MIME_TYPES.contains(contentType) && !contentType.startsWith("audio/"))) {
            throw new BadRequestException("Unsupported audio MIME type: " + contentType);
        }

        if (durationSeconds < 1 || durationSeconds > appProperties.audio().maxDurationSeconds()) {
            throw new BadRequestException("Audio duration must be between 1 and " + appProperties.audio().maxDurationSeconds() + " seconds");
        }
    }

    private boolean looksUnfaithful(String source, String formatted) {
        if (source == null || source.isBlank() || formatted == null || formatted.isBlank()) {
            return false;
        }

        String sourceLower = source.toLowerCase(Locale.ROOT);
        String formattedLower = formatted.toLowerCase(Locale.ROOT);
        for (String term : HIGH_RISK_ADDITIONS) {
            if (formattedLower.contains(term) && !sourceLower.contains(term)) {
                return true;
            }
        }

        int sourceWords = wordCount(source);
        int formattedWords = wordCount(formatted);
        return sourceWords > 0 && formattedWords > (sourceWords * 2) + 12;
    }

    private int wordCount(String text) {
        String trimmed = text == null ? "" : text.trim();
        if (trimmed.isEmpty()) {
            return 0;
        }
        return trimmed.split("\\s+").length;
    }

    private void deleteAudio(CallRecordEntity call) {
        if (call.getAudioObjectKey() == null) {
            return;
        }
        String key = call.getAudioObjectKey();
        audioStorageService.delete(key);
        call.setAudioObjectKey(null);
        callRecordRepository.save(call);
    }
}
