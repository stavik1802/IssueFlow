package com.att.tdp.issueflow.comment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.att.tdp.issueflow.audit.AuditAction;
import com.att.tdp.issueflow.audit.AuditEventPublisher;
import com.att.tdp.issueflow.audit.AuditableEntityType;
import com.att.tdp.issueflow.comment.dto.CommentResponse;
import com.att.tdp.issueflow.comment.dto.CreateCommentRequest;
import com.att.tdp.issueflow.comment.dto.UpdateCommentRequest;
import com.att.tdp.issueflow.common.exception.NotFoundException;
import com.att.tdp.issueflow.project.Project;
import com.att.tdp.issueflow.ticket.Ticket;
import com.att.tdp.issueflow.ticket.TicketRepository;
import com.att.tdp.issueflow.user.Role;
import com.att.tdp.issueflow.user.User;
import com.att.tdp.issueflow.user.UserRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CommentServiceTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-05-19T10:15:30Z"), ZoneOffset.UTC);

    @Mock
    private CommentRepository commentRepository;

    @Mock
    private TicketRepository ticketRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private MentionService mentionService;

    @Mock
    private AuditEventPublisher auditEventPublisher;

    private CommentService commentService;

    @BeforeEach
    void setUp() {
        commentService = new CommentService(
                commentRepository,
                ticketRepository,
                userRepository,
                mentionService,
                new CommentMapper(),
                CLOCK,
                auditEventPublisher
        );
    }

    @Test
    void createsComment() {
        Ticket ticket = ticket();
        User author = author();
        when(ticketRepository.findActiveWithActiveProjectById(10L)).thenReturn(Optional.of(ticket));
        when(userRepository.findById(1L)).thenReturn(Optional.of(author));
        when(mentionService.syncMentions(any(Comment.class)))
                .thenReturn(new MentionService.MentionSyncResult(List.of(), List.of()));
        when(commentRepository.saveAndFlush(any(Comment.class))).thenAnswer(invocation -> {
            Comment comment = invocation.getArgument(0);
            comment.setId(100L);
            return comment;
        });

        CommentResponse response = commentService.create(10L, new CreateCommentRequest(" Hello @alice ", 1L));

        ArgumentCaptor<Comment> commentCaptor = ArgumentCaptor.forClass(Comment.class);
        verify(commentRepository).saveAndFlush(commentCaptor.capture());
        verify(mentionService).syncMentions(commentCaptor.getValue());
        assertThat(response.id()).isEqualTo(100L);
        assertThat(response.ticketId()).isEqualTo(10L);
        assertThat(response.authorId()).isEqualTo(1L);
        assertThat(response.content()).isEqualTo("Hello @alice");
    }

    @Test
    void createFailsWhenTicketDoesNotExist() {
        when(ticketRepository.findActiveWithActiveProjectById(404L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> commentService.create(404L, new CreateCommentRequest("Hello", 1L)))
                .isInstanceOf(NotFoundException.class)
                .hasMessage("Ticket not found");
    }

    @Test
    void updatesCommentAndReevaluatesMentions() {
        Comment comment = comment();
        when(commentRepository.findByIdForUpdate(100L)).thenReturn(Optional.of(comment));
        when(mentionService.syncMentions(comment))
                .thenReturn(new MentionService.MentionSyncResult(List.of(), List.of()));
        when(commentRepository.saveAndFlush(comment)).thenReturn(comment);

        CommentResponse response = commentService.update(100L, new UpdateCommentRequest("Updated @bob", null));

        verify(mentionService).syncMentions(comment);
        assertThat(comment.getBody()).isEqualTo("Updated @bob");
        assertThat(comment.getEditedAt()).isEqualTo(Instant.parse("2026-05-19T10:15:30Z"));
        assertThat(response.content()).isEqualTo("Updated @bob");
        verify(auditEventPublisher).userAction(
                eq(1L),
                eq(AuditAction.UPDATE),
                eq(AuditableEntityType.COMMENT),
                eq(100L),
                any(),
                any()
        );
    }

    @Test
    void deletesComment() {
        Comment comment = comment();
        when(commentRepository.findById(100L)).thenReturn(Optional.of(comment));

        commentService.delete(100L);

        verify(commentRepository).delete(comment);
    }

    private static Comment comment() {
        Comment comment = new Comment();
        comment.setId(100L);
        comment.setTicket(ticket());
        comment.setAuthor(author());
        comment.setBody("Original @alice");
        return comment;
    }

    private static Ticket ticket() {
        Ticket ticket = new Ticket();
        ticket.setId(10L);
        ticket.setTitle("Ticket");
        Project project = new Project();
        project.setId(20L);
        ticket.setProject(project);
        return ticket;
    }

    private static User author() {
        User user = new User();
        user.setId(1L);
        user.setUsername("author");
        user.setEmail("author@example.com");
        user.setFullName("Author");
        user.setRole(Role.DEVELOPER);
        return user;
    }
}
