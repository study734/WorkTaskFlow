package com.teamproject.common.storage;

import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import java.util.concurrent.TimeUnit;

@RestController
public class ImageController {
    private final ImageStorageService images;

    public ImageController(ImageStorageService images) {
        this.images = images;
    }

    @GetMapping("/uploads/{category}/{filename:.+}")
    ResponseEntity<byte[]> image(@PathVariable String category, @PathVariable String filename) {
        var file = images.load(category, filename);
        MediaType contentType;
        try {
            contentType = MediaType.parseMediaType(file.contentType());
        } catch (RuntimeException exception) {
            contentType = MediaType.APPLICATION_OCTET_STREAM;
        }
        return ResponseEntity.ok()
                .contentType(contentType)
                .cacheControl(CacheControl.maxAge(1, TimeUnit.DAYS).cachePrivate())
                .body(file.content());
    }
}
