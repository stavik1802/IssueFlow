package com.att.tdp.issueflow.comment;

import com.att.tdp.issueflow.common.persistence.AuditableEntity;
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
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(
        name = "mentions",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_mentions_comment_user",
                columnNames = {"comment_id", "mentioned_user_id"}
        ),
        indexes = {
                @Index(name = "idx_mentions_user_created", columnList = "mentioned_user_id, created_at"),
                @Index(name = "idx_mentions_ticket_id", columnList = "ticket_id")
        }
)
public class Mention extends AuditableEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "comment_id", nullable = false, foreignKey = @ForeignKey(name = "fk_mentions_comment"))
    private Comment comment;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "ticket_id", nullable = false, foreignKey = @ForeignKey(name = "fk_mentions_ticket"))
    private Ticket ticket;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
            name = "mentioned_user_id",
            nullable = false,
            foreignKey = @ForeignKey(name = "fk_mentions_mentioned_user")
    )
    private User mentionedUser;

    @Column(name = "start_offset")
    private Integer startOffset;

    @Column(name = "end_offset")
    private Integer endOffset;
}
