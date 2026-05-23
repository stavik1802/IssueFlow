package com.att.tdp.issueflow.ticket;

import jakarta.persistence.LockModeType;
import jakarta.persistence.QueryHint;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.QueryHints;
import org.springframework.data.repository.query.Param;

public interface TicketRepository extends JpaRepository<Ticket, Long> {

    List<Ticket> findByProjectIdOrderByCreatedAtDesc(Long projectId);

    @Query(value = "SELECT * FROM tickets WHERE project_id = :projectId AND deleted_at IS NULL ORDER BY created_at DESC", nativeQuery = true)
    List<Ticket> findActiveByProjectIdIncludingDeletedProject(@Param("projectId") Long projectId);

    @Query(value = "SELECT id FROM tickets WHERE project_id = :projectId AND deleted_at IS NULL ORDER BY created_at DESC", nativeQuery = true)
    List<Long> findActiveIdsByProjectIdIncludingDeletedProject(@Param("projectId") Long projectId);

    @Query("""
            select t
            from Ticket t
            where t.id = :id
              and t.project.deletedAt is null
            """)
    Optional<Ticket> findActiveWithActiveProjectById(@Param("id") Long id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @QueryHints(@QueryHint(name = "jakarta.persistence.lock.timeout", value = "0"))
    @Query("""
            select t
            from Ticket t
            where t.id = :id
              and t.project.deletedAt is null
            """)
    Optional<Ticket> findActiveWithActiveProjectByIdForUpdate(@Param("id") Long id);

    @Query("""
            select count(t) > 0
            from Ticket t
            where t.id = :id
              and t.project.deletedAt is null
            """)
    boolean existsActiveWithActiveProjectById(@Param("id") Long id);

    @Query(value = """
            SELECT COUNT(*) > 0
            FROM tickets t
            JOIN projects p ON p.id = t.project_id
            WHERE t.deleted_at IS NULL
              AND p.deleted_at IS NULL
              AND t.project_id = :projectId
              AND t.reporter_id = :reporterId
              AND ((:assigneeId IS NULL AND t.assignee_id IS NULL) OR t.assignee_id = :assigneeId)
              AND t.title = :title
              AND ((:description IS NULL AND t.description IS NULL) OR t.description = :description)
              AND t.status = :status
              AND t.priority = :priority
              AND t.type = :type
              AND t.due_at IS NULL
            """, nativeQuery = true)
    boolean existsDuplicateActiveTicketWithoutDueAt(
            @Param("projectId") Long projectId,
            @Param("reporterId") Long reporterId,
            @Param("assigneeId") Long assigneeId,
            @Param("title") String title,
            @Param("description") String description,
            @Param("status") String status,
            @Param("priority") String priority,
            @Param("type") String type
    );

    @Query(value = """
            SELECT COUNT(*) > 0
            FROM tickets t
            JOIN projects p ON p.id = t.project_id
            WHERE t.deleted_at IS NULL
              AND p.deleted_at IS NULL
              AND t.project_id = :projectId
              AND t.reporter_id = :reporterId
              AND ((:assigneeId IS NULL AND t.assignee_id IS NULL) OR t.assignee_id = :assigneeId)
              AND t.title = :title
              AND ((:description IS NULL AND t.description IS NULL) OR t.description = :description)
              AND t.status = :status
              AND t.priority = :priority
              AND t.type = :type
              AND t.due_at = :dueAt
            """, nativeQuery = true)
    boolean existsDuplicateActiveTicketWithDueAt(
            @Param("projectId") Long projectId,
            @Param("reporterId") Long reporterId,
            @Param("assigneeId") Long assigneeId,
            @Param("title") String title,
            @Param("description") String description,
            @Param("status") String status,
            @Param("priority") String priority,
            @Param("type") String type,
            @Param("dueAt") Instant dueAt
    );

    @Query("""
            select t
            from Ticket t
            where t.dueAt < :dueAt
              and t.status <> :status
              and (
                    t.priority <> com.att.tdp.issueflow.ticket.TicketPriority.CRITICAL
                    or t.overdue = false
                  )
            """)
    List<Ticket> findEscalationCandidates(@Param("dueAt") Instant dueAt, @Param("status") TicketStatus status);

    @Query(value = "SELECT * FROM tickets WHERE id = :id", nativeQuery = true)
    Optional<Ticket> findIncludingDeletedById(@Param("id") Long id);

    @Query(
            value = "SELECT * FROM tickets WHERE project_id = :projectId AND deleted_at IS NOT NULL ORDER BY deleted_at DESC",
            nativeQuery = true
    )
    List<Ticket> findDeletedByProjectId(@Param("projectId") Long projectId);

    @Query(
            value = "SELECT * FROM tickets WHERE project_id = :projectId AND deleted_at IS NOT NULL AND deleted_by = 0 ORDER BY deleted_at DESC",
            nativeQuery = true
    )
    List<Ticket> findSystemDeletedByProjectId(@Param("projectId") Long projectId);

    @Query(
            value = "SELECT id FROM tickets WHERE project_id = :projectId AND deleted_at IS NOT NULL AND deleted_by = 0 ORDER BY deleted_at DESC",
            nativeQuery = true
    )
    List<Long> findSystemDeletedIdsByProjectId(@Param("projectId") Long projectId);

    @Modifying
    @Query(value = "UPDATE tickets SET deleted_at = now(), deleted_by = 0, version = version + 1 WHERE project_id = :projectId AND deleted_at IS NULL", nativeQuery = true)
    int softDeleteActiveByProjectIdAsSystem(@Param("projectId") Long projectId);

    @Modifying
    @Query(value = "UPDATE tickets SET deleted_at = NULL, deleted_by = NULL, version = version + 1 WHERE project_id = :projectId AND deleted_at IS NOT NULL AND deleted_by = 0", nativeQuery = true)
    int restoreSystemDeletedByProjectId(@Param("projectId") Long projectId);

    @Modifying
    @Query(value = "UPDATE tickets SET deleted_at = now(), deleted_by = 0, version = version + 1 WHERE id IN (:ids) AND deleted_at IS NULL", nativeQuery = true)
    int softDeleteActiveByIdsAsSystem(@Param("ids") Collection<Long> ids);

    @Modifying
    @Query(value = "UPDATE tickets SET deleted_at = NULL, deleted_by = NULL WHERE id = :id AND deleted_at IS NOT NULL", nativeQuery = true)
    int restoreById(@Param("id") Long id);

    @Query("""
            select t.assignee.id as userId, count(t) as openTicketCount
            from Ticket t
            where t.project.id = :projectId
              and t.assignee is not null
              and t.status <> com.att.tdp.issueflow.ticket.TicketStatus.DONE
            group by t.assignee.id
            """)
    List<AssigneeOpenTicketCount> countOpenTicketsByAssignee(@Param("projectId") Long projectId);

    interface AssigneeOpenTicketCount {

        Long getUserId();

        long getOpenTicketCount();
    }
}
