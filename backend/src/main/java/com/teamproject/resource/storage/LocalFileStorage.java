package com.teamproject.resource.storage;

import com.teamproject.common.exception.ApplicationException;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import java.io.IOException;
import java.nio.file.*;

@Component
@ConditionalOnProperty(name = "app.storage.provider", havingValue = "local", matchIfMissing = true)
public class LocalFileStorage implements FileStorage {
    private final Path root;
    public LocalFileStorage(@Value("${app.storage.local-root:uploads}") String root) {
        this.root = Path.of(root).toAbsolutePath().normalize();
    }
    @Override public void put(String key, byte[] content, String contentType) {
        Path target = resolve(key);
        try {
            Files.createDirectories(target.getParent());
            Path temporary = Files.createTempFile(target.getParent(), ".upload-", ".tmp");
            try {
                Files.write(temporary, content);
                Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } finally { Files.deleteIfExists(temporary); }
        } catch (IOException exception) { throw storageFailure(); }
    }
    @Override public StoredFile get(String key) {
        try { return new StoredFile(Files.readAllBytes(resolve(key)), Files.probeContentType(resolve(key))); }
        catch (IOException exception) { throw new ApplicationException("RESOURCE_FILE_NOT_FOUND", HttpStatus.NOT_FOUND, "파일을 찾을 수 없습니다."); }
    }
    @Override public void delete(String key) {
        try { Files.deleteIfExists(resolve(key)); } catch (IOException ignored) {}
    }
    private Path resolve(String key) {
        Path value = root.resolve(key).normalize();
        if (!value.startsWith(root)) throw new ApplicationException("STORAGE_KEY_INVALID", HttpStatus.BAD_REQUEST, "올바르지 않은 저장 경로입니다.");
        return value;
    }
    private ApplicationException storageFailure() {
        return new ApplicationException("RESOURCE_STORAGE_FAILED", HttpStatus.INTERNAL_SERVER_ERROR, "파일을 저장하지 못했습니다.");
    }
}
