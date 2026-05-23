package com.att.tdp.issueflow.attachment;

import org.springframework.core.io.Resource;
import org.springframework.web.multipart.MultipartFile;

public interface AttachmentStorageService {

    StoredAttachment store(MultipartFile file);

    Resource load(String storageKey);

    void delete(String storageKey);

    record StoredAttachment(
            String originalFilename,
            String storedFilename,
            String contentType,
            long size,
            String storageKey
    ) {
    }
}
