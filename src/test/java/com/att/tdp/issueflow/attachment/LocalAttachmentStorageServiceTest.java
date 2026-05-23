package com.att.tdp.issueflow.attachment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;

class LocalAttachmentStorageServiceTest {

    @TempDir
    private Path uploadDirectory;

    @Test
    void storesValidFileUsingSafeGeneratedFilename() throws Exception {
        LocalAttachmentStorageService storageService = new LocalAttachmentStorageService(uploadDirectory.toString());
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "..\\unsafe report.PDF",
                "application/pdf",
                "%PDF-1.7\ncontent".getBytes()
        );

        AttachmentStorageService.StoredAttachment stored = storageService.store(file);

        assertThat(stored.originalFilename()).isEqualTo("unsafe report.PDF");
        assertThat(stored.storedFilename()).endsWith(".pdf");
        assertThat(stored.storedFilename()).doesNotContain("..", "/", "\\");
        assertThat(Files.exists(uploadDirectory.resolve(stored.storageKey()))).isTrue();
    }

    @Test
    void generatedFilenameDoesNotReuseOriginalBasename() {
        LocalAttachmentStorageService storageService = new LocalAttachmentStorageService(uploadDirectory.toString());

        String storedFilename = storageService.generateStoredFilename("customer-data.txt");

        assertThat(storedFilename).endsWith(".txt");
        assertThat(storedFilename).doesNotContain("customer-data");
        assertThat(storedFilename).doesNotContain("..", "/", "\\");
    }

    @Test
    void storesAllowedFileTypesAfterContentValidation() {
        LocalAttachmentStorageService storageService = new LocalAttachmentStorageService(uploadDirectory.toString());

        assertThat(storageService.store(file("pixel.png", "image/png",
                new byte[] {(byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1A, '\n', 0})).contentType())
                .isEqualTo("image/png");
        assertThat(storageService.store(file("photo.jpg", "image/jpeg",
                new byte[] {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, 0x00})).contentType())
                .isEqualTo("image/jpeg");
        assertThat(storageService.store(file("doc.pdf", "application/pdf",
                "%PDF-1.7\n".getBytes())).contentType())
                .isEqualTo("application/pdf");
        assertThat(storageService.store(file("notes.txt", "text/plain",
                "plain text\n".getBytes())).contentType())
                .isEqualTo("text/plain");
    }

    @Test
    void rejectsSpoofedMimeType() {
        LocalAttachmentStorageService storageService = new LocalAttachmentStorageService(uploadDirectory.toString());

        assertThatThrownBy(() -> storageService.store(file("pixel.png", "image/png",
                new byte[] {0x01, 0x02, 0x03, 0x04})))
                .hasMessage("Attachment MIME type is not allowed");
    }

    private MockMultipartFile file(String filename, String contentType, byte[] content) {
        return new MockMultipartFile("file", filename, contentType, content);
    }
}
