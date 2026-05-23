package com.att.tdp.issueflow.attachment;

import com.att.tdp.issueflow.common.persistence.SoftDeletableAuditableEntity;
import com.att.tdp.issueflow.ticket.Ticket;
import com.att.tdp.issueflow.user.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.SQLRestriction;

@Getter
@Setter
@Entity
@Table(
        name = "attachments",
        indexes = {
                @Index(name = "idx_attachments_ticket_created", columnList = "ticket_id, created_at"),
                @Index(name = "idx_attachments_uploaded_by", columnList = "uploaded_by_id"),
                @Index(name = "idx_attachments_storage_key", columnList = "storage_key")
        }
)
@SQLDelete(sql = "UPDATE attachments SET deleted_at = now(), version = version + 1 WHERE id = ? AND version = ?")
@SQLRestriction("deleted_at IS NULL")
public class Attachment extends SoftDeletableAuditableEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "ticket_id", nullable = false, foreignKey = @ForeignKey(name = "fk_attachments_ticket"))
    private Ticket ticket;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "uploaded_by_id", nullable = false, foreignKey = @ForeignKey(name = "fk_attachments_uploaded_by"))
    private User uploadedBy;

    @NotBlank
    @Column(name = "file_name", nullable = false, length = 255)
    private String fileName;

    @NotBlank
    @Column(name = "content_type", nullable = false, length = 120)
    private String contentType;

    @Column(name = "size_bytes", nullable = false)
    private long sizeBytes;

    @NotBlank
    @Column(name = "storage_key", nullable = false, length = 512)
    private String storageKey;

    @Column(name = "checksum_sha256", length = 64)
    private String checksumSha256;
}
