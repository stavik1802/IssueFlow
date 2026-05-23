package com.att.tdp.issueflow.audit;

import com.att.tdp.issueflow.audit.dto.AuditLogResponse;
import org.springframework.stereotype.Component;

@Component
public class AuditEventPublisher {

    private final AuditLogService auditLogService;

    public AuditEventPublisher(AuditLogService auditLogService) {
        this.auditLogService = auditLogService;
    }

    public AuditLogResponse userAction(
            Long actorId,
            AuditAction action,
            AuditableEntityType entityType,
            Long entityId,
            Object oldValue,
            Object newValue
    ) {
        return auditLogService.createLog(
                AuditActorType.USER,
                actorId,
                action,
                entityType,
                entityId,
                oldValue,
                newValue
        );
    }

    public AuditLogResponse systemAction(
            AuditAction action,
            AuditableEntityType entityType,
            Long entityId,
            Object oldValue,
            Object newValue
    ) {
        return systemAction(null, action, entityType, entityId, oldValue, newValue);
    }

    public AuditLogResponse systemAction(
            Long actorId,
            AuditAction action,
            AuditableEntityType entityType,
            Long entityId,
            Object oldValue,
            Object newValue
    ) {
        return auditLogService.createLog(
                AuditActorType.SYSTEM,
                actorId,
                action,
                entityType,
                entityId,
                oldValue,
                newValue
        );
    }
}
