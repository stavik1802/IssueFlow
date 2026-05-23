package com.att.tdp.issueflow.attachment;

import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AttachmentRepository extends JpaRepository<Attachment, Long> {

    List<Attachment> findByTicketIdOrderByCreatedAtDesc(Long ticketId);

    @Query(value = "SELECT * FROM attachments WHERE ticket_id = :ticketId AND deleted_at IS NULL ORDER BY created_at DESC", nativeQuery = true)
    List<Attachment> findActiveByTicketIdIncludingDeletedTicket(@Param("ticketId") Long ticketId);

    @Query(value = "SELECT id FROM attachments WHERE ticket_id = :ticketId AND deleted_at IS NULL ORDER BY created_at DESC", nativeQuery = true)
    List<Long> findActiveIdsByTicketIdIncludingDeletedTicket(@Param("ticketId") Long ticketId);

    @Query(value = "SELECT * FROM attachments WHERE ticket_id IN (:ticketIds) AND deleted_at IS NULL ORDER BY created_at DESC", nativeQuery = true)
    List<Attachment> findActiveByTicketIdsIncludingDeletedTickets(@Param("ticketIds") Collection<Long> ticketIds);

    @Query(value = "SELECT id FROM attachments WHERE ticket_id IN (:ticketIds) AND deleted_at IS NULL ORDER BY created_at DESC", nativeQuery = true)
    List<Long> findActiveIdsByTicketIdsIncludingDeletedTickets(@Param("ticketIds") Collection<Long> ticketIds);

    @Query(value = "SELECT * FROM attachments WHERE ticket_id = :ticketId AND deleted_at IS NOT NULL AND deleted_by = 0 ORDER BY deleted_at DESC", nativeQuery = true)
    List<Attachment> findSystemDeletedByTicketId(@Param("ticketId") Long ticketId);

    @Query(value = "SELECT id FROM attachments WHERE ticket_id = :ticketId AND deleted_at IS NOT NULL AND deleted_by = 0 ORDER BY deleted_at DESC", nativeQuery = true)
    List<Long> findSystemDeletedIdsByTicketId(@Param("ticketId") Long ticketId);

    @Query(value = "SELECT * FROM attachments WHERE ticket_id IN (:ticketIds) AND deleted_at IS NOT NULL AND deleted_by = 0 ORDER BY deleted_at DESC", nativeQuery = true)
    List<Attachment> findSystemDeletedByTicketIds(@Param("ticketIds") Collection<Long> ticketIds);

    @Query(value = "SELECT id FROM attachments WHERE ticket_id IN (:ticketIds) AND deleted_at IS NOT NULL AND deleted_by = 0 ORDER BY deleted_at DESC", nativeQuery = true)
    List<Long> findSystemDeletedIdsByTicketIds(@Param("ticketIds") Collection<Long> ticketIds);

    @Modifying
    @Query(value = "UPDATE attachments SET deleted_at = now(), deleted_by = 0, version = version + 1 WHERE ticket_id = :ticketId AND deleted_at IS NULL", nativeQuery = true)
    int softDeleteActiveByTicketIdAsSystem(@Param("ticketId") Long ticketId);

    @Modifying
    @Query(value = "UPDATE attachments SET deleted_at = now(), deleted_by = 0, version = version + 1 WHERE ticket_id IN (:ticketIds) AND deleted_at IS NULL", nativeQuery = true)
    int softDeleteActiveByTicketIdsAsSystem(@Param("ticketIds") Collection<Long> ticketIds);

    @Modifying
    @Query(value = "UPDATE attachments SET deleted_at = NULL, deleted_by = NULL, version = version + 1 WHERE ticket_id = :ticketId AND deleted_at IS NOT NULL AND deleted_by = 0", nativeQuery = true)
    int restoreSystemDeletedByTicketId(@Param("ticketId") Long ticketId);

    @Modifying
    @Query(value = "UPDATE attachments SET deleted_at = NULL, deleted_by = NULL, version = version + 1 WHERE ticket_id IN (:ticketIds) AND deleted_at IS NOT NULL AND deleted_by = 0", nativeQuery = true)
    int restoreSystemDeletedByTicketIds(@Param("ticketIds") Collection<Long> ticketIds);
}
