package com.somtranscriber.calls.model;

public enum CallStatus {
    CREATED,
    UPLOADED,
    TRANSCRIBING,
    FORMATTING,
    READY,
    READY_WITH_WARNING,
    FAILED,
    FINALIZED
}
