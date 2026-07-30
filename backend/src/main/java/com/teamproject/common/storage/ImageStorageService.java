package com.teamproject.common.storage;

import com.teamproject.common.exception.ApplicationException;
import com.teamproject.resource.storage.FileStorage;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import java.io.IOException;
import java.util.Iterator;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

@Service
public class ImageStorageService {
    private static final Set<String> ALLOWED_FORMATS = Set.of("jpeg", "jpg", "png", "gif");
    private static final Set<String> ALLOWED_CATEGORIES = Set.of("profiles", "groups");
    private static final long MAX_BYTES = 5L * 1024 * 1024;
    private static final int MAX_DIMENSION = 4096;
    private final FileStorage storage;

    public ImageStorageService(FileStorage storage) {
        this.storage = storage;
    }

    public String store(MultipartFile file, String category) {
        if (file == null || file.isEmpty() || file.getSize() > MAX_BYTES) {
            throw invalid("5MB 이하의 이미지 파일을 선택해 주세요.");
        }
        ImageInfo image = inspect(file);
        String safeCategory = ALLOWED_CATEGORIES.contains(category) ? category : "images";
        String extension = image.format().equals("jpeg") || image.format().equals("jpg") ? "jpg" : image.format();
        String filename = UUID.randomUUID() + "." + extension;
        try {
            String storageKey = key(safeCategory, filename);
            storage.put(storageKey, file.getBytes(), "image/" + ("jpg".equals(extension) ? "jpeg" : extension));
            deleteOnRollback(storageKey);
            return "/uploads/" + safeCategory + "/" + filename;
        } catch (IOException exception) {
            throw new ApplicationException("IMAGE_STORAGE_FAILED", HttpStatus.INTERNAL_SERVER_ERROR,
                    "이미지를 저장하지 못했습니다.");
        }
    }

    public void deleteManaged(String url) {
        if (url == null || !url.startsWith("/uploads/")) return;
        String[] parts = url.substring("/uploads/".length()).split("/", 2);
        if (parts.length != 2 || !valid(parts[0], parts[1])) return;
        storage.delete(key(parts[0], parts[1]));
    }

    public void deleteManagedAfterCommit(String url) {
        if (url == null) return;
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            deleteManaged(url);
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override public void afterCommit() { deleteManaged(url); }
        });
    }

    public FileStorage.StoredFile load(String category, String filename) {
        if (!valid(category, filename)) {
            throw new ApplicationException("IMAGE_NOT_FOUND", HttpStatus.NOT_FOUND, "이미지를 찾을 수 없습니다.");
        }
        return storage.get(key(category, filename));
    }

    private ImageInfo inspect(MultipartFile file) {
        try (ImageInputStream input = ImageIO.createImageInputStream(file.getInputStream())) {
            if (input == null) throw invalid("올바른 이미지 파일이 아닙니다.");
            Iterator<ImageReader> readers = ImageIO.getImageReaders(input);
            if (!readers.hasNext()) throw invalid("JPG, PNG 또는 GIF 이미지만 사용할 수 있습니다.");
            ImageReader reader = readers.next();
            try {
                reader.setInput(input, true, true);
                String format = reader.getFormatName().toLowerCase(Locale.ROOT);
                int width = reader.getWidth(0);
                int height = reader.getHeight(0);
                if (!ALLOWED_FORMATS.contains(format) || width < 1 || height < 1
                        || width > MAX_DIMENSION || height > MAX_DIMENSION) {
                    throw invalid("4096px 이하의 JPG, PNG 또는 GIF 이미지만 사용할 수 있습니다.");
                }
                return new ImageInfo(format);
            } finally {
                reader.dispose();
            }
        } catch (IOException exception) {
            throw invalid("이미지 파일을 확인할 수 없습니다.");
        }
    }

    private ApplicationException invalid(String message) {
        return new ApplicationException("IMAGE_INVALID", HttpStatus.BAD_REQUEST, message);
    }

    private boolean valid(String category, String filename) {
        return ALLOWED_CATEGORIES.contains(category)
                && filename.matches("[0-9a-fA-F-]{36}\\.(jpg|png|gif)");
    }

    private String key(String category, String filename) {
        return category + "/" + filename;
    }

    private void deleteOnRollback(String storageKey) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) return;
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCompletion(int status) {
                if (status != TransactionSynchronization.STATUS_COMMITTED) storage.delete(storageKey);
            }
        });
    }

    private record ImageInfo(String format) {}
}
