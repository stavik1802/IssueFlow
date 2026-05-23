package com.att.tdp.issueflow.ticket;

import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TicketDependencyRepository extends JpaRepository<TicketDependency, Long> {

    @Query(value = """
            SELECT COUNT(*) > 0
            FROM ticket_dependencies td
            JOIN tickets t ON t.id = td.ticket_id
            JOIN projects tp ON tp.id = t.project_id
            JOIN tickets b ON b.id = td.depends_on_ticket_id
            JOIN projects bp ON bp.id = b.project_id
            WHERE td.ticket_id = :ticketId
              AND td.depends_on_ticket_id = :dependsOnTicketId
              AND td.deleted_at IS NULL
              AND t.deleted_at IS NULL
              AND tp.deleted_at IS NULL
              AND b.deleted_at IS NULL
              AND bp.deleted_at IS NULL
            """, nativeQuery = true)
    boolean existsByTicketIdAndDependsOnTicketId(
            @Param("ticketId") Long ticketId,
            @Param("dependsOnTicketId") Long dependsOnTicketId
    );

    @Query(value = """
            SELECT td.*
            FROM ticket_dependencies td
            JOIN tickets t ON t.id = td.ticket_id
            JOIN projects tp ON tp.id = t.project_id
            JOIN tickets b ON b.id = td.depends_on_ticket_id
            JOIN projects bp ON bp.id = b.project_id
            WHERE td.ticket_id = :ticketId
              AND td.deleted_at IS NULL
              AND t.deleted_at IS NULL
              AND tp.deleted_at IS NULL
              AND b.deleted_at IS NULL
              AND bp.deleted_at IS NULL
            ORDER BY td.created_at DESC
            """, nativeQuery = true)
    List<TicketDependency> findByTicketIdOrderByCreatedAtDesc(@Param("ticketId") Long ticketId);

    @Modifying
    @Query(value = """
            DELETE FROM ticket_dependencies
            WHERE ticket_id = :ticketId
              AND depends_on_ticket_id = :dependsOnTicketId
              AND deleted_at IS NULL
            """, nativeQuery = true)
    void deleteByTicketIdAndDependsOnTicketId(
            @Param("ticketId") Long ticketId,
            @Param("dependsOnTicketId") Long dependsOnTicketId
    );

    @Query(value = """
            SELECT COUNT(*) > 0
            FROM ticket_dependencies td
            JOIN tickets t ON t.id = td.ticket_id
            JOIN projects tp ON tp.id = t.project_id
            JOIN tickets b ON b.id = td.depends_on_ticket_id
            JOIN projects bp ON bp.id = b.project_id
            WHERE td.ticket_id = :ticketId
              AND td.deleted_at IS NULL
              AND t.deleted_at IS NULL
              AND tp.deleted_at IS NULL
              AND b.deleted_at IS NULL
              AND bp.deleted_at IS NULL
              AND b.status <> 'DONE'
            """, nativeQuery = true)
    boolean existsUnresolvedBlocker(@Param("ticketId") Long ticketId);

    @Query(value = """
            SELECT id
            FROM ticket_dependencies
            WHERE deleted_at IS NULL
              AND (ticket_id = :ticketId OR depends_on_ticket_id = :ticketId)
            ORDER BY created_at DESC
            """, nativeQuery = true)
    List<Long> findActiveIdsTouchingTicketId(@Param("ticketId") Long ticketId);

    @Query(value = """
            SELECT id
            FROM ticket_dependencies
            WHERE deleted_at IS NOT NULL
              AND deleted_by = 0
              AND (ticket_id = :ticketId OR depends_on_ticket_id = :ticketId)
            ORDER BY deleted_at DESC
            """, nativeQuery = true)
    List<Long> findSystemDeletedIdsTouchingTicketId(@Param("ticketId") Long ticketId);

    @Query(value = """
            SELECT id
            FROM ticket_dependencies
            WHERE deleted_at IS NULL
              AND (ticket_id IN (:ticketIds) OR depends_on_ticket_id IN (:ticketIds))
            ORDER BY created_at DESC
            """, nativeQuery = true)
    List<Long> findActiveIdsTouchingTicketIds(@Param("ticketIds") Collection<Long> ticketIds);

    @Query(value = """
            SELECT id
            FROM ticket_dependencies
            WHERE deleted_at IS NOT NULL
              AND deleted_by = 0
              AND (ticket_id IN (:ticketIds) OR depends_on_ticket_id IN (:ticketIds))
            ORDER BY deleted_at DESC
            """, nativeQuery = true)
    List<Long> findSystemDeletedIdsTouchingTicketIds(@Param("ticketIds") Collection<Long> ticketIds);

    @Modifying
    @Query(value = """
            UPDATE ticket_dependencies
            SET deleted_at = now(), deleted_by = 0, version = version + 1
            WHERE deleted_at IS NULL
              AND (ticket_id = :ticketId OR depends_on_ticket_id = :ticketId)
            """, nativeQuery = true)
    int softDeleteActiveTouchingTicketIdAsSystem(@Param("ticketId") Long ticketId);

    @Modifying
    @Query(value = """
            UPDATE ticket_dependencies
            SET deleted_at = now(), deleted_by = 0, version = version + 1
            WHERE deleted_at IS NULL
              AND (ticket_id IN (:ticketIds) OR depends_on_ticket_id IN (:ticketIds))
            """, nativeQuery = true)
    int softDeleteActiveTouchingTicketIdsAsSystem(@Param("ticketIds") Collection<Long> ticketIds);

    @Modifying
    @Query(value = """
            UPDATE ticket_dependencies
            SET deleted_at = NULL, deleted_by = NULL, version = version + 1
            WHERE deleted_at IS NOT NULL
              AND deleted_by = 0
              AND (ticket_id = :ticketId OR depends_on_ticket_id = :ticketId)
            """, nativeQuery = true)
    int restoreSystemDeletedTouchingTicketId(@Param("ticketId") Long ticketId);

    @Modifying
    @Query(value = """
            UPDATE ticket_dependencies
            SET deleted_at = NULL, deleted_by = NULL, version = version + 1
            WHERE deleted_at IS NOT NULL
              AND deleted_by = 0
              AND (ticket_id IN (:ticketIds) OR depends_on_ticket_id IN (:ticketIds))
            """, nativeQuery = true)
    int restoreSystemDeletedTouchingTicketIds(@Param("ticketIds") Collection<Long> ticketIds);
}
