package com.somtranscriber.calls.model;

import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "call_records")
public class CallRecordEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "call_at", nullable = false)
    private Instant callAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private CallStatus status;

    @Column(name = "audio_object_key")
    private String audioObjectKey;

    @Column(name = "detected_language")
    private String detectedLanguage;

    @Column(name = "transcript_english", columnDefinition = "TEXT")
    private String transcriptEnglish;

    @Column(name = "transcript_model")
    private String transcriptModel;

    @Column(name = "transcript_latency_ms")
    private Long transcriptLatencyMs;

    @Column(name = "note_text", columnDefinition = "TEXT")
    private String noteText;

    @Enumerated(EnumType.STRING)
    @Column(name = "note_source")
    private NoteSource noteSource;

    @Column(columnDefinition = "TEXT")
    private String warning;

    @Column(name = "final_text", columnDefinition = "TEXT")
    private String finalText;

    @Column(name = "finalized_at")
    private Instant finalizedAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    public void prePersist() {
        Instant now = Instant.now();
        if (createdAt == null) {
            createdAt = now;
        }
        if (updatedAt == null) {
            updatedAt = now;
        }
    }

    @PreUpdate
    public void preUpdate() {
        updatedAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public UUID getUserId() {
        return userId;
    }

    public void setUserId(UUID userId) {
        this.userId = userId;
    }

    public Instant getCallAt() {
        return callAt;
    }

    public void setCallAt(Instant callAt) {
        this.callAt = callAt;
    }

    public CallStatus getStatus() {
        return status;
    }

    public void setStatus(CallStatus status) {
        this.status = status;
    }

    public String getAudioObjectKey() {
        return audioObjectKey;
    }

    public void setAudioObjectKey(String audioObjectKey) {
        this.audioObjectKey = audioObjectKey;
    }

    public String getDetectedLanguage() {
        return detectedLanguage;
    }

    public void setDetectedLanguage(String detectedLanguage) {
        this.detectedLanguage = detectedLanguage;
    }

    public String getTranscriptEnglish() {
        return transcriptEnglish;
    }

    public void setTranscriptEnglish(String transcriptEnglish) {
        this.transcriptEnglish = transcriptEnglish;
    }

    public String getTranscriptModel() {
        return transcriptModel;
    }

    public void setTranscriptModel(String transcriptModel) {
        this.transcriptModel = transcriptModel;
    }

    public Long getTranscriptLatencyMs() {
        return transcriptLatencyMs;
    }

    public void setTranscriptLatencyMs(Long transcriptLatencyMs) {
        this.transcriptLatencyMs = transcriptLatencyMs;
    }

    public String getNoteText() {
        return noteText;
    }

    public void setNoteText(String noteText) {
        this.noteText = noteText;
    }

    public NoteSource getNoteSource() {
        return noteSource;
    }

    public void setNoteSource(NoteSource noteSource) {
        this.noteSource = noteSource;
    }

    public String getWarning() {
        return warning;
    }

    public void setWarning(String warning) {
        this.warning = warning;
    }

    public String getFinalText() {
        return finalText;
    }

    public void setFinalText(String finalText) {
        this.finalText = finalText;
    }

    public Instant getFinalizedAt() {
        return finalizedAt;
    }

    public void setFinalizedAt(Instant finalizedAt) {
        this.finalizedAt = finalizedAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }
}
