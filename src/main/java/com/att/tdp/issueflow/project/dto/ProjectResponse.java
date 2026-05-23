package com.att.tdp.issueflow.project.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import java.time.Instant;

@JsonPropertyOrder({"id", "name", "description", "ownerId"})
public record ProjectResponse(
        Long id,
        @JsonIgnore
        String key,
        String name,
        String description,
        Long ownerId,
        @JsonIgnore
        String ownerUsername,
        @JsonIgnore
        int memberCount,
        @JsonIgnore
        Instant createdAt,
        @JsonIgnore
        Instant updatedAt,
        @JsonIgnore
        Instant deletedAt
) {
}
