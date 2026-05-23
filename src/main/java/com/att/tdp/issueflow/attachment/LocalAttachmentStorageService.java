package com.att.tdp.issueflow.attachment;

import com.att.tdp.issueflow.common.exception.BadRequestException;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

@Service
public class LocalAttachmentStorageService implements AttachmentStorageService {

    static final long MAX_FILE_SIZE_BYTES = 10L * 1024L * 1024L;

    private static final Set<String> ALLOWED_CONTENT_TYPES = Set.of(
            "image/png",
            "image/jpeg",
            "application/pdf",
            "text/plain"
    );

    private final Path uploadDirectory;

    public LocalAttachmentStorageService(
            @Value("${issueflow.attachments.upload-directory:uploads/attachments}") String uploadDirectory
    ) {
        this.uploadDirectory = Paths.get(uploadDirectory).toAbsolutePath().normalize();
    }

    @Override
    public StoredAttachment store(MultipartFile file) {
        String contentType = validate(file);
        String originalFilename = sanitizeOriginalFilename(file.getOriginalFilename());
        String storedFilename = generateStoredFilename(originalFilename);
        Path destination = resolveStoragePath(storedFilename);

        try {
            Files.createDirectories(uploadDirectory);
            Files.copy(file.getInputStream(), destination, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException exception) {
            throw new UncheckedIOException("Failed to store attachment", exception);
        }

        return new StoredAttachment(
                originalFilename,
                storedFilename,
                contentType,
                file.getSize(),
                storedFilename
        );
    }

    @Override
    public Resource load(String storageKey) {
        Path file = resolveStoragePath(storageKey);
        try {
            Resource resource = new UrlResource(file.toUri());
            if (!resource.exists() || !resource.isReadable()) {
                throw new BadRequestException("Attachment file is not available");
            }
            return resource;
        } catch (IOException exception) {
            throw new BadRequestException("Attachment file is not available", exception);
        }
    }

    @Override
    public void delete(String storageKey) {
        Path file = resolveStoragePath(storageKey);
        try {
            Files.deleteIfExists(file);
        } catch (IOException exception) {
            throw new UncheckedIOException("Failed to delete attachment", exception);
        }
    }

    String generateStoredFilename(String originalFilename) {
        String extension = StringUtils.getFilenameExtension(originalFilename);
        String safeExtension = extension == null ? "" : "." + extension.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
        return UUID.randomUUID() + safeExtension;
    }

    private String validate(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BadRequestException("Attachment file is required");
        }
        if (file.getSize() > MAX_FILE_SIZE_BYTES) {
            throw new BadRequestException("Attachment exceeds the 10 MB size limit");
        }
        String providedContentType = normalizeContentType(file.getContentType());
        if (!ALLOWED_CONTENT_TYPES.contains(providedContentType)) {
            throw new BadRequestException("Attachment MIME type is not allowed");
        }
        String detectedContentType = detectContentType(file);
        if (!providedContentType.equals(detectedContentType)) {
            throw new BadRequestException("Attachment MIME type is not allowed");
        }
        return detectedContentType;
    }

    private String detectContentType(MultipartFile file) {
        byte[] bytes;
        try {
            bytes = file.getBytes();
        } catch (IOException exception) {
            throw new BadRequestException("Attachment file is not readable", exception);
        }
        if (startsWith(bytes, new byte[] {(byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1A, '\n'})) {
            return "image/png";
        }
        if (bytes.length >= 3
                && (bytes[0] & 0xFF) == 0xFF
                && (bytes[1] & 0xFF) == 0xD8
                && (bytes[2] & 0xFF) == 0xFF) {
            return "image/jpeg";
        }
        if (startsWith(bytes, "%PDF-".getBytes(StandardCharsets.US_ASCII))) {
            return "application/pdf";
        }
        if (isPlainText(bytes)) {
            return "text/plain";
        }
        return null;
    }

    private boolean startsWith(byte[] bytes, byte[] prefix) {
        if (bytes.length < prefix.length) {
            return false;
        }
        for (int index = 0; index < prefix.length; index++) {
            if (bytes[index] != prefix[index]) {
                return false;
            }
        }
        return true;
    }

    private boolean isPlainText(byte[] bytes) {
        for (byte value : bytes) {
            int unsigned = value & 0xFF;
            if (unsigned == 0 || (unsigned < 0x09) || (unsigned > 0x0D && unsigned < 0x20)) {
                return false;
            }
        }
        try {
            StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(bytes));
            return true;
        } catch (CharacterCodingException exception) {
            return false;
        }
    }

    private String normalizeContentType(String contentType) {
        if (contentType == null) {
            return null;
        }
        int parametersStart = contentType.indexOf(';');
        String mediaType = parametersStart < 0 ? contentType : contentType.substring(0, parametersStart);
        return mediaType.trim().toLowerCase(Locale.ROOT);
    }

    private String sanitizeOriginalFilename(String originalFilename) {
        String filename = StringUtils.cleanPath(originalFilename == null ? "attachment" : originalFilename);
        Path filenamePath = Paths.get(filename).getFileName();
        String safeFilename = filenamePath == null ? "attachment" : filenamePath.toString();
        return safeFilename.isBlank() ? "attachment" : safeFilename;
    }

    private Path resolveStoragePath(String storageKey) {
        String filename = Paths.get(storageKey).getFileName().toString();
        Path resolved = uploadDirectory.resolve(filename).normalize();
        if (!resolved.startsWith(uploadDirectory)) {
            throw new BadRequestException("Invalid attachment path");
        }
        return resolved;
    }
}
