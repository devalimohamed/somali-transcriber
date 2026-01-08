CREATE TABLE users (
    id UUID PRIMARY KEY,
    email VARCHAR(255) NOT NULL UNIQUE,
    password_hash TEXT NOT NULL,
    role VARCHAR(32) NOT NULL,
    status VARCHAR(32) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE TABLE invites (
    id UUID PRIMARY KEY,
    email VARCHAR(255) NOT NULL,
    token_hash TEXT NOT NULL UNIQUE,
    expires_at TIMESTAMP WITH TIME ZONE NOT NULL,
    accepted_at TIMESTAMP WITH TIME ZONE,
    created_by UUID NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE TABLE refresh_tokens (
    id UUID PRIMARY KEY,
    token_id VARCHAR(255) NOT NULL UNIQUE,
    token_hash TEXT NOT NULL UNIQUE,
    user_id UUID NOT NULL,
    expires_at TIMESTAMP WITH TIME ZONE NOT NULL,
    revoked_at TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE TABLE call_records (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL,
    place VARCHAR(255) NOT NULL,
    call_at TIMESTAMP WITH TIME ZONE NOT NULL,
    status VARCHAR(32) NOT NULL,
    audio_object_key TEXT,
    detected_language VARCHAR(64),
    transcript_english TEXT,
    transcript_model VARCHAR(128),
    transcript_latency_ms BIGINT,
    note_text TEXT,
    note_source VARCHAR(32),
    warning TEXT,
    final_text TEXT,
    finalized_at TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE TABLE job_attempts (
    id UUID PRIMARY KEY,
    call_id UUID NOT NULL,
    stage VARCHAR(32) NOT NULL,
    attempt_no INT NOT NULL,
    error_code VARCHAR(128),
    next_retry_at TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE INDEX idx_call_records_user_call_at ON call_records(user_id, call_at DESC);
CREATE INDEX idx_job_attempts_call_stage ON job_attempts(call_id, stage);
