package com.att.tdp.issueflow.ticket;

import com.att.tdp.issueflow.attachment.Attachment;
import com.att.tdp.issueflow.comment.Comment;
import com.att.tdp.issueflow.common.persistence.SoftDeletableAuditableEntity;
import com.att.tdp.issueflow.project.Project;
import com.att.tdp.issueflow.user.User;
import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotBlank;
import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.Set;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.BatchSize;
import org.hibernate.annotations.NotFound;
import org.hibernate.annotations.NotFoundAction;
import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.SQLRestriction;

@Getter
@Setter
@Entity
@Table(
        name = "tickets",
        indexes = {
                @Index(name = "idx_tickets_project_status", columnList = "project_id, status"),
                @Index(name = "idx_tickets_assignee_status", columnList = "assignee_id, status"),
                @Index(name = "idx_tickets_reporter_id", columnList = "reporter_id"),
                @Index(name = "idx_tickets_priority", columnList = "priority"),
                @Index(name = "idx_tickets_due_at", columnList = "due_at")
        }
)
@SQLDelete(sql = "UPDATE tickets SET deleted_at = now(), version = version + 1 WHERE id = ? AND version = ?")
@SQLRestriction("deleted_at IS NULL")
public class Ticket extends SoftDeletableAuditableEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "project_id", nullable = false, foreignKey = @ForeignKey(name = "fk_tickets_project"))
    private Project project;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "reporter_id", nullable = false, foreignKey = @ForeignKey(name = "fk_tickets_reporter"))
    @NotFound(action = NotFoundAction.IGNORE)
    private User reporter;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "assignee_id", foreignKey = @ForeignKey(name = "fk_tickets_assignee"))
    @NotFound(action = NotFoundAction.IGNORE)
    private User assignee;

    @NotBlank
    @Column(nullable = false, length = 240)
    private String title;

    @Column(columnDefinition = "text")
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private TicketType type = TicketType.FEATURE;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private TicketStatus status = TicketStatus.TODO;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private TicketPriority priority = TicketPriority.MEDIUM;

    @Column(name = "due_at")
    private Instant dueAt;

    @Column(name = "is_overdue", nullable = false)
    private boolean overdue;

    @Column(name = "resolved_at")
    private Instant resolvedAt;

    @JsonIgnore
    @BatchSize(size = 50)
    @OneToMany(mappedBy = "ticket", cascade = CascadeType.ALL, orphanRemoval = true)
    private Set<Comment> comments = new LinkedHashSet<>();

    @JsonIgnore
    @BatchSize(size = 50)
    @OneToMany(mappedBy = "ticket", cascade = CascadeType.ALL, orphanRemoval = true)
    private Set<Attachment> attachments = new LinkedHashSet<>();

    @JsonIgnore
    @BatchSize(size = 50)
    @OneToMany(mappedBy = "ticket", cascade = CascadeType.ALL, orphanRemoval = true)
    private Set<TicketDependency> dependencies = new LinkedHashSet<>();

    @JsonIgnore
    @BatchSize(size = 50)
    @OneToMany(mappedBy = "dependsOnTicket")
    private Set<TicketDependency> blockedTickets = new LinkedHashSet<>();
}
