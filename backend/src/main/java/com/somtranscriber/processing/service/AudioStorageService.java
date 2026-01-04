package com.somtranscriber.processing.service;

import org.springframework.web.multipart.MultipartFile;

import java.nio.file.Path;

public interface AudioStorageService {
    String store(MultipartFile file);

    Path resolve(String key);

    void delete(String key);
}
