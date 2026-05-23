package com.att.tdp.issueflow.ticket;

import com.att.tdp.issueflow.common.persistence.SoftDeletableAuditableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.Check;
import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.SQLRestriction;

@Getter
@Setter
@Entity
@Table(
        name = "ticket_dependencies",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_ticket_dependencies_edge",
                columnNames = {"ticket_id", "depends_on_ticket_id"}
        ),
        indexes = {
                @Index(name = "idx_ticket_dependencies_depends_on", columnList = "depends_on_ticket_id"),
                @Index(name = "idx_ticket_dependencies_ticket", columnList = "ticket_id")
        }
)
@Check(constraints = "ticket_id <> depends_on_ticket_id")
@SQLDelete(sql = "UPDATE ticket_dependencies SET deleted_at = now(), version = version + 1 WHERE id = ? AND version = ?")
@SQLRestriction("deleted_at IS NULL")
public class TicketDependency extends SoftDeletableAuditableEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "ticket_id", nullable = false, foreignKey = @ForeignKey(name = "fk_ticket_dependencies_ticket"))
    private Ticket ticket;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
            name = "depends_on_ticket_id",
            nullable = false,
            foreignKey = @ForeignKey(name = "fk_ticket_dependencies_depends_on")
    )
    private Ticket dependsOnTicket;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private TicketDependencyType type = TicketDependencyType.BLOCKED_BY;
}
