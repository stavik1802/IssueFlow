package com.att.tdp.issueflow.common.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.MappedSuperclass;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@MappedSuperclass
public abstract class SoftDeletableAuditableEntity extends SoftDeletable {

    @Column(name = "deleted_by")
    private Long deletedBy;

    public void markDeleted(Long deletedBy) {
        markDeleted();
        this.deletedBy = deletedBy;
    }

    @Override
    public void restore() {
        super.restore();
        this.deletedBy = null;
    }
}
