package com.att.tdp.issueflow.attachment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.att.tdp.issueflow.attachment.dto.AttachmentResponse;
import com.att.tdp.issueflow.audit.AuditEventPublisher;
import com.att.tdp.issueflow.common.exception.BadRequestException;
import com.att.tdp.issueflow.security.auth.CurrentUser;
import com.att.tdp.issueflow.ticket.Ticket;
import com.att.tdp.issueflow.ticket.TicketRepository;
import com.att.tdp.issueflow.user.Role;
import com.att.tdp.issueflow.user.User;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;

@ExtendWith(MockitoExtension.class)
class AttachmentServiceTest {

    @Mock
    private AttachmentRepository attachmentRepository;

    @Mock
    private TicketRepository ticketRepository;

    @Mock
    private AttachmentStorageService storageService;

    @Mock
    private AuditEventPublisher auditEventPublisher;

    private AttachmentService attachmentService;

    @BeforeEach
    void setUp() {
        attachmentService = new AttachmentService(
                attachmentRepository,
                ticketRepository,
                storageService,
                auditEventPublisher
        );
    }

    @Test
    void uploadsValidFile() {
        MockMultipartFile file = new MockMultipartFile("file", "report.pdf", "application/pdf", "hello".getBytes());
        when(ticketRepository.findActiveWithActiveProjectById(10L)).thenReturn(Optional.of(ticket()));
        when(storageService.store(file)).thenReturn(new AttachmentStorageService.StoredAttachment(
                "report.pdf",
                "safe-name.pdf",
                "application/pdf",
                5L,
                "safe-name.pdf"
        ));
        when(attachmentRepository.saveAndFlush(any(Attachment.class))).thenAnswer(invocation -> {
            Attachment attachment = invocation.getArgument(0);
            attachment.setId(100L);
            attachment.setCreatedAt(Instant.parse("2026-05-19T10:15:30Z"));
            return attachment;
        });

        AttachmentResponse response = attachmentService.upload(10L, file, currentUser());

        ArgumentCaptor<Attachment> attachmentCaptor = ArgumentCaptor.forClass(Attachment.class);
        verify(attachmentRepository).saveAndFlush(attachmentCaptor.capture());
        Attachment saved = attachmentCaptor.getValue();
        assertThat(saved.getTicket().getId()).isEqualTo(10L);
        assertThat(saved.getUploadedBy().getId()).isEqualTo(1L);
        assertThat(saved.getFileName()).isEqualTo("report.pdf");
        assertThat(saved.getStorageKey()).isEqualTo("safe-name.pdf");
        assertThat(response.id()).isEqualTo(100L);
        assertThat(response.ticketId()).isEqualTo(10L);
        assertThat(response.originalFilename()).isEqualTo("report.pdf");
        assertThat(response.storedFilename()).isEqualTo("safe-name.pdf");
        assertThat(response.mimeType()).isEqualTo("application/pdf");
        verify(auditEventPublisher).userAction(any(), any(), any(), any(), any(), any());
    }

    @Test
    void rejectsTooLargeFile() {
        LocalAttachmentStorageService storage = new LocalAttachmentStorageService("target/test-attachments");
        byte[] content = new byte[(int) LocalAttachmentStorageService.MAX_FILE_SIZE_BYTES + 1];
        MockMultipartFile file = new MockMultipartFile("file", "big.txt", "text/plain", content);

        assertThatThrownBy(() -> storage.store(file))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("Attachment exceeds the 10 MB size limit");
    }

    @Test
    void rejectsInvalidMimeType() {
        LocalAttachmentStorageService storage = new LocalAttachmentStorageService("target/test-attachments");
        MockMultipartFile file = new MockMultipartFile("file", "script.js", "application/javascript", "x".getBytes());

        assertThatThrownBy(() -> storage.store(file))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("Attachment MIME type is not allowed");
    }

    @Test
    void listsTicketAttachments() {
        when(ticketRepository.existsActiveWithActiveProjectById(10L)).thenReturn(true);
        Attachment attachment = attachment("notes.txt", "stored.txt", "text/plain", 4L);
        when(attachmentRepository.findByTicketIdOrderByCreatedAtDesc(10L)).thenReturn(List.of(attachment));

        List<AttachmentResponse> responses = attachmentService.listForTicket(10L);

        assertThat(responses).hasSize(1);
        assertThat(responses.getFirst().ticketId()).isEqualTo(10L);
        assertThat(responses.getFirst().originalFilename()).isEqualTo("notes.txt");
    }

    private static Attachment attachment(String originalFilename, String storageKey, String contentType, long size) {
        Attachment attachment = new Attachment();
        attachment.setId(100L);
        attachment.setTicket(ticket());
        attachment.setUploadedBy(user());
        attachment.setFileName(originalFilename);
        attachment.setStorageKey(storageKey);
        attachment.setContentType(contentType);
        attachment.setSizeBytes(size);
        attachment.setCreatedAt(Instant.parse("2026-05-19T10:15:30Z"));
        return attachment;
    }

    private static Ticket ticket() {
        Ticket ticket = new Ticket();
        ticket.setId(10L);
        ticket.setTitle("Ticket");
        ticket.setReporter(user());
        return ticket;
    }

    private static User user() {
        User user = new User();
        user.setId(1L);
        user.setUsername("author");
        user.setEmail("author@example.com");
        user.setFullName("Author");
        user.setRole(Role.DEVELOPER);
        return user;
    }

    private static CurrentUser currentUser() {
        return new CurrentUser(1L, "author", "author@example.com", "Author", Role.DEVELOPER);
    }
}
