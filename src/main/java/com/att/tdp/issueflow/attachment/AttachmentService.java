package com.att.tdp.issueflow.attachment;

import com.att.tdp.issueflow.attachment.dto.AttachmentResponse;
import com.att.tdp.issueflow.audit.AuditAction;
import com.att.tdp.issueflow.audit.AuditEventPublisher;
import com.att.tdp.issueflow.audit.AuditableEntityType;
import com.att.tdp.issueflow.common.exception.ForbiddenException;
import com.att.tdp.issueflow.common.exception.NotFoundException;
import com.att.tdp.issueflow.security.auth.CurrentUser;
import com.att.tdp.issueflow.ticket.Ticket;
import com.att.tdp.issueflow.ticket.TicketRepository;
import com.att.tdp.issueflow.user.User;
import java.nio.file.Paths;
import java.util.List;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
public class AttachmentService {

    private static final String ATTACHMENT_NOT_FOUND = "Attachment not found";

    private final AttachmentRepository attachmentRepository;
    private final TicketRepository ticketRepository;
    private final AttachmentStorageService storageService;
    private final AuditEventPublisher auditEventPublisher;

    public AttachmentService(
            AttachmentRepository attachmentRepository,
            TicketRepository ticketRepository,
            AttachmentStorageService storageService,
            AuditEventPublisher auditEventPublisher
    ) {
        this.attachmentRepository = attachmentRepository;
        this.ticketRepository = ticketRepository;
        this.storageService = storageService;
        this.auditEventPublisher = auditEventPublisher;
    }

    @Transactional
    public AttachmentResponse upload(Long ticketId, MultipartFile file, CurrentUser currentUser) {
        requireAuthenticated(currentUser);
        Ticket ticket = ticketRepository.findActiveWithActiveProjectById(ticketId)
                .orElseThrow(() -> new NotFoundException("Ticket not found"));
        User uploader = ticket.getReporter();

        AttachmentStorageService.StoredAttachment storedAttachment = storageService.store(file);
        try {
            Attachment attachment = new Attachment();
            attachment.setTicket(ticket);
            attachment.setUploadedBy(uploader);
            attachment.setFileName(storedAttachment.originalFilename());
            attachment.setContentType(storedAttachment.contentType());
            attachment.setSizeBytes(storedAttachment.size());
            attachment.setStorageKey(storedAttachment.storageKey());

            Attachment saved = attachmentRepository.saveAndFlush(attachment);
            auditEventPublisher.userAction(
                    currentUser.id(),
                    AuditAction.UPLOAD_ATTACHMENT,
                    AuditableEntityType.ATTACHMENT,
                    saved.getId(),
                    null,
                    toResponse(saved)
            );
            return toResponse(saved);
        } catch (RuntimeException exception) {
            storageService.delete(storedAttachment.storageKey());
            throw exception;
        }
    }

    @Transactional(readOnly = true)
    public List<AttachmentResponse> listForTicket(Long ticketId) {
        if (!ticketRepository.existsActiveWithActiveProjectById(ticketId)) {
            throw new NotFoundException("Ticket not found");
        }
        return attachmentRepository.findByTicketIdOrderByCreatedAtDesc(ticketId).stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public DownloadAttachment download(Long attachmentId) {
        Attachment attachment = findAttachment(attachmentId);
        return new DownloadAttachment(toResponse(attachment), storageService.load(attachment.getStorageKey()));
    }

    @Transactional
    public void delete(Long attachmentId, CurrentUser currentUser) {
        requireAuthenticated(currentUser);
        Attachment attachment = findAttachment(attachmentId);
        deleteAttachment(attachment, attachmentId, currentUser);
    }

    @Transactional
    public void delete(Long ticketId, Long attachmentId, CurrentUser currentUser) {
        requireAuthenticated(currentUser);
        Attachment attachment = findAttachment(attachmentId);
        if (!attachment.getTicket().getId().equals(ticketId)) {
            throw new NotFoundException(ATTACHMENT_NOT_FOUND);
        }
        deleteAttachment(attachment, attachmentId, currentUser);
    }

    private void deleteAttachment(Attachment attachment, Long attachmentId, CurrentUser currentUser) {
        AttachmentResponse oldValue = toResponse(attachment);
        String storageKey = attachment.getStorageKey();

        attachmentRepository.delete(attachment);
        storageService.delete(storageKey);
        auditEventPublisher.userAction(
                currentUser.id(),
                AuditAction.DELETE_ATTACHMENT,
                AuditableEntityType.ATTACHMENT,
                attachmentId,
                oldValue,
                null
        );
    }

    private Attachment findAttachment(Long attachmentId) {
        Attachment attachment = attachmentRepository.findById(attachmentId)
                .orElseThrow(() -> new NotFoundException(ATTACHMENT_NOT_FOUND));
        if (!ticketRepository.existsActiveWithActiveProjectById(attachment.getTicket().getId())) {
            throw new NotFoundException(ATTACHMENT_NOT_FOUND);
        }
        return attachment;
    }

    private AttachmentResponse toResponse(Attachment attachment) {
        return new AttachmentResponse(
                attachment.getId(),
                attachment.getTicket().getId(),
                attachment.getFileName(),
                Paths.get(attachment.getStorageKey()).getFileName().toString(),
                attachment.getContentType(),
                attachment.getSizeBytes(),
                attachment.getStorageKey(),
                attachment.getCreatedAt()
        );
    }

    private void requireAuthenticated(CurrentUser currentUser) {
        if (currentUser == null || currentUser.id() == null) {
            throw new ForbiddenException("Authentication is required");
        }
    }

    public record DownloadAttachment(AttachmentResponse attachment, Resource resource) {
    }
}
