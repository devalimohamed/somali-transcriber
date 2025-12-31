package com.somtranscriber.processing.adapter;

import com.somtranscriber.config.AppProperties;
import com.somtranscriber.processing.service.AudioStorageService;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.UUID;

@Service
public class LocalAudioStorageService implements AudioStorageService {

    private final Path root;

    public LocalAudioStorageService(AppProperties properties) {
        this.root = Path.of(properties.audio().storageDir());
        try {
            Files.createDirectories(root);
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to initialize audio storage directory", exception);
        }
    }

    @Override
    public String store(MultipartFile file) {
        String filename = file.getOriginalFilename() == null ? "audio" : file.getOriginalFilename();
        String extension = "";
        int index = filename.lastIndexOf('.');
        if (index >= 0) {
            extension = filename.substring(index);
        }

        String key = UUID.randomUUID() + extension;
        Path target = root.resolve(key);

        try {
            Files.copy(file.getInputStream(), target, StandardCopyOption.REPLACE_EXISTING);
            return key;
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to store uploaded audio", exception);
        }
    }

    @Override
    public Path resolve(String key) {
        return root.resolve(key);
    }

    @Override
    public void delete(String key) {
        if (key == null || key.isBlank()) {
            return;
        }

        try {
            Files.deleteIfExists(root.resolve(key));
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to delete stored audio", exception);
        }
    }
}
