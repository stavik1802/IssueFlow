package com.att.tdp.issueflow.attachment.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import java.time.Instant;

@JsonPropertyOrder({"id", "ticketId", "filename", "contentType"})
public record AttachmentResponse(
        Long id,
        Long ticketId,
        @JsonIgnore
        String originalFilename,
        @JsonIgnore
        String storedFilename,
        @JsonIgnore
        String mimeType,
        @JsonIgnore
        long size,
        @JsonIgnore
        String path,
        @JsonIgnore
        Instant createdAt
) {
    @JsonProperty("filename")
    public String filename() {
        return originalFilename;
    }

    @JsonProperty("contentType")
    public String contentType() {
        return mimeType;
    }
}
