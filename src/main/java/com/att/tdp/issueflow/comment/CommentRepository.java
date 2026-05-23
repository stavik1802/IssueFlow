package com.att.tdp.issueflow.comment;

import jakarta.persistence.LockModeType;
import jakarta.persistence.QueryHint;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.QueryHints;
import org.springframework.data.repository.query.Param;

public interface CommentRepository extends JpaRepository<Comment, Long> {

    @EntityGraph(attributePaths = {"author", "ticket", "mentions"})
    List<Comment> findByTicketIdOrderByCreatedAtAsc(Long ticketId);

    @Query("""
            select count(c) > 0
            from Comment c
            where c.ticket.id = :ticketId
              and c.ticket.project.deletedAt is null
              and c.author.id = :authorId
              and c.body = :body
            """)
    boolean existsDuplicateActiveComment(
            @Param("ticketId") Long ticketId,
            @Param("authorId") Long authorId,
            @Param("body") String body
    );

    @Query(value = "SELECT * FROM comments WHERE ticket_id = :ticketId AND deleted_at IS NULL ORDER BY created_at ASC", nativeQuery = true)
    List<Comment> findActiveByTicketIdIncludingDeletedTicket(@Param("ticketId") Long ticketId);

    @Query(value = "SELECT id FROM comments WHERE ticket_id = :ticketId AND deleted_at IS NULL ORDER BY created_at ASC", nativeQuery = true)
    List<Long> findActiveIdsByTicketIdIncludingDeletedTicket(@Param("ticketId") Long ticketId);

    @Query(value = "SELECT * FROM comments WHERE ticket_id IN (:ticketIds) AND deleted_at IS NULL ORDER BY created_at ASC", nativeQuery = true)
    List<Comment> findActiveByTicketIdsIncludingDeletedTickets(@Param("ticketIds") Collection<Long> ticketIds);

    @Query(value = "SELECT id FROM comments WHERE ticket_id IN (:ticketIds) AND deleted_at IS NULL ORDER BY created_at ASC", nativeQuery = true)
    List<Long> findActiveIdsByTicketIdsIncludingDeletedTickets(@Param("ticketIds") Collection<Long> ticketIds);

    @Query(value = "SELECT * FROM comments WHERE ticket_id = :ticketId AND deleted_at IS NOT NULL AND deleted_by = 0 ORDER BY deleted_at DESC", nativeQuery = true)
    List<Comment> findSystemDeletedByTicketId(@Param("ticketId") Long ticketId);

    @Query(value = "SELECT id FROM comments WHERE ticket_id = :ticketId AND deleted_at IS NOT NULL AND deleted_by = 0 ORDER BY deleted_at DESC", nativeQuery = true)
    List<Long> findSystemDeletedIdsByTicketId(@Param("ticketId") Long ticketId);

    @Query(value = "SELECT * FROM comments WHERE ticket_id IN (:ticketIds) AND deleted_at IS NOT NULL AND deleted_by = 0 ORDER BY deleted_at DESC", nativeQuery = true)
    List<Comment> findSystemDeletedByTicketIds(@Param("ticketIds") Collection<Long> ticketIds);

    @Query(value = "SELECT id FROM comments WHERE ticket_id IN (:ticketIds) AND deleted_at IS NOT NULL AND deleted_by = 0 ORDER BY deleted_at DESC", nativeQuery = true)
    List<Long> findSystemDeletedIdsByTicketIds(@Param("ticketIds") Collection<Long> ticketIds);

    @Modifying
    @Query(value = "UPDATE comments SET deleted_at = now(), deleted_by = 0, version = version + 1 WHERE ticket_id = :ticketId AND deleted_at IS NULL", nativeQuery = true)
    int softDeleteActiveByTicketIdAsSystem(@Param("ticketId") Long ticketId);

    @Modifying
    @Query(value = "UPDATE comments SET deleted_at = now(), deleted_by = 0, version = version + 1 WHERE ticket_id IN (:ticketIds) AND deleted_at IS NULL", nativeQuery = true)
    int softDeleteActiveByTicketIdsAsSystem(@Param("ticketIds") Collection<Long> ticketIds);

    @Modifying
    @Query(value = "UPDATE comments SET deleted_at = NULL, deleted_by = NULL, version = version + 1 WHERE ticket_id = :ticketId AND deleted_at IS NOT NULL AND deleted_by = 0", nativeQuery = true)
    int restoreSystemDeletedByTicketId(@Param("ticketId") Long ticketId);

    @Modifying
    @Query(value = "UPDATE comments SET deleted_at = NULL, deleted_by = NULL, version = version + 1 WHERE ticket_id IN (:ticketIds) AND deleted_at IS NOT NULL AND deleted_by = 0", nativeQuery = true)
    int restoreSystemDeletedByTicketIds(@Param("ticketIds") Collection<Long> ticketIds);

    @Query("""
            select m.comment.id as commentId,
                   u.id as id,
                   u.username as username,
                   u.fullName as fullName
            from Mention m
            join m.mentionedUser u
            where m.comment.id in :commentIds
            order by lower(u.username)
            """)
    List<ActiveMentionedUserView> findActiveMentionedUsersByCommentIds(@Param("commentIds") Collection<Long> commentIds);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @QueryHints(@QueryHint(name = "jakarta.persistence.lock.timeout", value = "0"))
    @Query("""
            select c
            from Comment c
            where c.id = :id
    """)
    Optional<Comment> findByIdForUpdate(@Param("id") Long id);

    interface ActiveMentionedUserView {
        Long getCommentId();

        Long getId();

        String getUsername();

        String getFullName();
    }
}
