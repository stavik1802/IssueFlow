package com.att.tdp.issueflow.audit.dto;

import com.att.tdp.issueflow.audit.AuditAction;
import com.att.tdp.issueflow.audit.AuditActorType;
import com.att.tdp.issueflow.audit.AuditableEntityType;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import java.time.Instant;

@JsonPropertyOrder({"id", "action", "entityType", "entityId", "performedBy", "actor", "timestamp"})
public record AuditLogResponse(
        Long id,
        @JsonIgnore
        AuditActorType actorType,
        @JsonIgnore
        Long actorId,
        AuditAction action,
        AuditableEntityType entityType,
        Long entityId,
        @JsonIgnore
        String oldValue,
        @JsonIgnore
        String newValue,
        @JsonIgnore
        String summary,
        @JsonIgnore
        String metadataJson,
        @JsonIgnore
        Instant createdAt
) {
    @JsonProperty("performedBy")
    public Long performedBy() {
        return actorId;
    }

    @JsonProperty("actor")
    public AuditActorType actor() {
        return actorType;
    }

    @JsonProperty("timestamp")
    public Instant timestamp() {
        return createdAt;
    }
}
