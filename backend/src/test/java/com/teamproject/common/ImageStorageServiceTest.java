package com.teamproject.common;

import com.teamproject.common.exception.ApplicationException;
import com.teamproject.common.storage.ImageStorageService;
import com.teamproject.resource.storage.LocalFileStorage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ImageStorageServiceTest {
    @TempDir Path directory;

    @Test
    void validatesContentStoresWithGeneratedNameAndDeletesManagedFile() {
        byte[] png = Base64.getDecoder().decode(
                "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mNk+A8AAQUBAScY42YAAAAASUVORK5CYII=");
        ImageStorageService storage = new ImageStorageService(new LocalFileStorage(directory.toString()));

        String url = storage.store(new MockMultipartFile("file", "attacker.svg", "image/svg+xml", png), "profiles");

        assertThat(url).startsWith("/uploads/profiles/").endsWith(".png");
        Path stored = directory.resolve(url.substring("/uploads/".length()));
        assertThat(stored).exists();
        storage.deleteManaged(url);
        assertThat(stored).doesNotExist();
    }

    @Test
    void rejectsNonImageContentEvenWhenMimeTypeClaimsImage() {
        ImageStorageService storage = new ImageStorageService(new LocalFileStorage(directory.toString()));
        var file = new MockMultipartFile("file", "fake.png", "image/png", "not-an-image".getBytes());
        assertThatThrownBy(() -> storage.store(file, "profiles"))
                .isInstanceOf(ApplicationException.class);
        assertThat(Files.exists(directory.resolve("profiles"))).isFalse();
    }
}
