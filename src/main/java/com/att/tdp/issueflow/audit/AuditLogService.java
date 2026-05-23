package com.att.tdp.issueflow.audit;

import com.att.tdp.issueflow.audit.dto.AuditLogFilter;
import com.att.tdp.issueflow.audit.dto.AuditLogResponse;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuditLogService {

    private final AuditLogRepository auditLogRepository;
    private final ObjectMapper objectMapper;

    public AuditLogService(AuditLogRepository auditLogRepository, ObjectMapper objectMapper) {
        this.auditLogRepository = auditLogRepository;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public AuditLogResponse createLog(
            AuditActorType actorType,
            Long actorId,
            AuditAction action,
            AuditableEntityType entityType,
            Long entityId,
            Object oldValue,
            Object newValue
    ) {
        AuditLog auditLog = new AuditLog();
        auditLog.setActorType(actorType);
        auditLog.setActorId(actorId);
        auditLog.setAction(action);
        auditLog.setEntityType(entityType);
        auditLog.setEntityId(entityId);
        auditLog.setOldValue(toJson(oldValue));
        auditLog.setNewValue(toJson(newValue));
        auditLog.setSummary(summary(action, entityType, entityId));
        return toResponse(auditLogRepository.save(auditLog));
    }

    @Transactional(readOnly = true)
    public Page<AuditLogResponse> findLogs(AuditLogFilter filter, Pageable pageable) {
        return auditLogRepository.findAll(specification(filter), pageable)
                .map(this::toResponse);
    }

    private Specification<AuditLog> specification(AuditLogFilter filter) {
        return Specification.allOf(
                equalsIfPresent("actorType", filter.resolvedActorType()),
                equalsIfPresent("actorId", filter.actorId()),
                equalsIfPresent("action", filter.action()),
                equalsIfPresent("entityType", filter.entityType()),
                equalsIfPresent("entityId", filter.entityId()),
                filter.from() == null ? null : (root, query, builder) ->
                        builder.greaterThanOrEqualTo(root.get("createdAt"), filter.from()),
                filter.to() == null ? null : (root, query, builder) ->
                        builder.lessThanOrEqualTo(root.get("createdAt"), filter.to())
        );
    }

    private Specification<AuditLog> equalsIfPresent(String field, Object value) {
        if (value == null) {
            return null;
        }
        return (root, query, builder) -> builder.equal(root.get(field), value);
    }

    private AuditLogResponse toResponse(AuditLog auditLog) {
        return new AuditLogResponse(
                auditLog.getId(),
                auditLog.getActorType(),
                auditLog.getActorId(),
                auditLog.getAction(),
                auditLog.getEntityType(),
                auditLog.getEntityId(),
                auditLog.getOldValue(),
                auditLog.getNewValue(),
                auditLog.getSummary(),
                auditLog.getMetadataJson(),
                auditLog.getCreatedAt()
        );
    }

    private String toJson(Object value) {
        if (value == null) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("Audit value cannot be serialized to JSON", exception);
        }
    }

    private String summary(AuditAction action, AuditableEntityType entityType, Long entityId) {
        return action + " " + entityType + (entityId == null ? "" : " " + entityId);
    }
}
