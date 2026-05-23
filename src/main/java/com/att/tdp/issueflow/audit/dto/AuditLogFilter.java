package com.att.tdp.issueflow.audit.dto;

import com.att.tdp.issueflow.audit.AuditAction;
import com.att.tdp.issueflow.audit.AuditActorType;
import com.att.tdp.issueflow.audit.AuditableEntityType;
import java.time.Instant;
import org.springframework.format.annotation.DateTimeFormat;

public record AuditLogFilter(
        AuditActorType actorType,
        AuditActorType actor,
        Long actorId,
        AuditAction action,
        AuditableEntityType entityType,
        Long entityId,

        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
        Instant from,

        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
        Instant to
) {
    public AuditActorType resolvedActorType() {
        return actorType != null ? actorType : actor;
    }
}
