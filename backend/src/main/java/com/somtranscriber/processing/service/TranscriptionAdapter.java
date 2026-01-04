package com.somtranscriber.processing.service;

import java.nio.file.Path;

public interface TranscriptionAdapter {
    TranscriptionResult transcribe(Path filePath, String mimeType);
}
