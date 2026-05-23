package com.att.tdp.issueflow.common.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.MappedSuperclass;
import java.time.Instant;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@MappedSuperclass
public abstract class SoftDeletable extends AuditableEntity {

    /*
     * Keep deleted_at as the single persisted source of truth used by the current
     * schema and Hibernate restrictions. The deleted property is derived to avoid
     * duplicating state while still giving services a simple boolean check.
     */
    @Column(name = "deleted_at")
    private Instant deletedAt;

    public boolean isDeleted() {
        return deletedAt != null;
    }

    public void markDeleted() {
        deletedAt = Instant.now();
    }

    public void restore() {
        deletedAt = null;
    }
}
