package com.att.tdp.issueflow.ticket;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.att.tdp.issueflow.audit.AuditAction;
import com.att.tdp.issueflow.audit.AuditEventPublisher;
import com.att.tdp.issueflow.audit.AuditableEntityType;
import com.att.tdp.issueflow.attachment.AttachmentRepository;
import com.att.tdp.issueflow.comment.CommentMapper;
import com.att.tdp.issueflow.comment.CommentRepository;
import com.att.tdp.issueflow.common.exception.BusinessRuleViolationException;
import com.att.tdp.issueflow.common.exception.NotFoundException;
import com.att.tdp.issueflow.project.Project;
import com.att.tdp.issueflow.project.ProjectRepository;
import com.att.tdp.issueflow.ticket.dto.CreateTicketRequest;
import com.att.tdp.issueflow.ticket.dto.UpdateTicketRequest;
import com.att.tdp.issueflow.user.Role;
import com.att.tdp.issueflow.user.User;
import com.att.tdp.issueflow.user.UserRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class TicketServiceTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-05-19T10:15:30Z"), ZoneOffset.UTC);

    @Mock
    private TicketRepository ticketRepository;

    @Mock
    private ProjectRepository projectRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private TicketAssignmentService ticketAssignmentService;

    @Mock
    private TicketDependencyService ticketDependencyService;

    @Mock
    private TicketDependencyRepository ticketDependencyRepository;

    @Mock
    private CommentRepository commentRepository;

    @Mock
    private AttachmentRepository attachmentRepository;

    @Mock
    private AuditEventPublisher auditEventPublisher;

    private TicketService ticketService;

    @BeforeEach
    void setUp() {
        ticketService = new TicketService(
                ticketRepository,
                projectRepository,
                userRepository,
                ticketAssignmentService,
                ticketDependencyService,
                ticketDependencyRepository,
                new TicketLifecyclePolicy(),
                new TicketMapper(),
                commentRepository,
                new CommentMapper(),
                attachmentRepository,
                CLOCK,
                auditEventPublisher
        );
    }

    @Test
    void createsTicket() {
        Project project = project();
        User reporter = user(1L, "reporter");
        when(projectRepository.findById(10L)).thenReturn(Optional.of(project));
        when(userRepository.findById(1L)).thenReturn(Optional.of(reporter));
        when(ticketRepository.save(any(Ticket.class))).thenAnswer(invocation -> {
            Ticket ticket = invocation.getArgument(0);
            ticket.setId(100L);
            return ticket;
        });

        var response = ticketService.create(createRequest(null));

        verify(ticketAssignmentService).autoAssignIfNeeded(any(Ticket.class));
        assertThat(response.id()).isEqualTo(100L);
        assertThat(response.projectId()).isEqualTo(10L);
        assertThat(response.title()).isEqualTo("Ticket title");
        assertThat(response.status()).isEqualTo(TicketStatus.TODO);
        assertThat(response.priority()).isEqualTo(TicketPriority.MEDIUM);
    }

    @Test
    void getsTicketById() {
        Ticket ticket = ticket(TicketStatus.TODO);
        when(ticketRepository.findActiveWithActiveProjectById(100L)).thenReturn(Optional.of(ticket));

        var response = ticketService.getById(100L);

        assertThat(response.id()).isEqualTo(100L);
        assertThat(response.title()).isEqualTo("Ticket title");
    }

    @Test
    void updatesTicketSuccessfully() {
        Ticket ticket = ticket(TicketStatus.TODO);
        User assignee = user(2L, "assignee");
        when(ticketRepository.findActiveWithActiveProjectByIdForUpdate(100L)).thenReturn(Optional.of(ticket));
        when(userRepository.findById(2L)).thenReturn(Optional.of(assignee));

        var response = ticketService.update(100L, new UpdateTicketRequest(
                2L,
                "Updated title",
                "Updated description",
                TicketPriority.HIGH,
                TicketStatus.IN_PROGRESS,
                Instant.parse("2026-05-20T10:15:30Z"),
                null
        ));

        assertThat(response.title()).isEqualTo("Updated title");
        assertThat(response.description()).isEqualTo("Updated description");
        assertThat(response.assigneeId()).isEqualTo(2L);
        assertThat(response.priority()).isEqualTo(TicketPriority.HIGH);
        assertThat(response.status()).isEqualTo(TicketStatus.IN_PROGRESS);
        assertThat(response.overdue()).isFalse();
        assertThat(response.dueDate()).isEqualTo(Instant.parse("2026-05-20T10:15:30Z"));
        verify(auditEventPublisher).userAction(
                eq(null),
                eq(AuditAction.UPDATE),
                eq(AuditableEntityType.TICKET),
                eq(100L),
                any(),
                any()
        );
    }

    @Test
    void rejectsBackwardStatusTransition() {
        Ticket ticket = ticket(TicketStatus.IN_REVIEW);
        when(ticketRepository.findActiveWithActiveProjectByIdForUpdate(100L)).thenReturn(Optional.of(ticket));

        assertThatThrownBy(() -> ticketService.update(100L, new UpdateTicketRequest(
                null,
                null,
                null,
                null,
                TicketStatus.IN_PROGRESS,
                null,
                null
        )))
                .isInstanceOf(BusinessRuleViolationException.class)
                .hasMessage("Ticket status cannot move backward");
    }

    @Test
    void rejectsSkippedForwardStatusTransition() {
        Ticket ticket = ticket(TicketStatus.TODO);
        when(ticketRepository.findActiveWithActiveProjectByIdForUpdate(100L)).thenReturn(Optional.of(ticket));

        assertThatThrownBy(() -> ticketService.update(100L, new UpdateTicketRequest(
                null,
                null,
                null,
                null,
                TicketStatus.IN_REVIEW,
                null,
                null
        )))
                .isInstanceOf(BusinessRuleViolationException.class)
                .hasMessage("Ticket status can only move one step forward");
    }

    @Test
    void rejectsDoneTicketUpdate() {
        Ticket ticket = ticket(TicketStatus.DONE);
        when(ticketRepository.findActiveWithActiveProjectByIdForUpdate(100L)).thenReturn(Optional.of(ticket));

        assertThatThrownBy(() -> ticketService.update(100L, new UpdateTicketRequest(
                null,
                "Cannot update",
                null,
                null,
                null,
                null,
                null
        )))
                .isInstanceOf(BusinessRuleViolationException.class)
                .hasMessage("DONE ticket cannot be updated");
    }

    @Test
    void preventsDoneWhenBlockerIsUnresolved() {
        Ticket ticket = ticket(TicketStatus.IN_REVIEW);
        when(ticketRepository.findActiveWithActiveProjectByIdForUpdate(100L)).thenReturn(Optional.of(ticket));
        org.mockito.Mockito.doThrow(new BusinessRuleViolationException(
                        "Ticket cannot move to DONE while blockers are unresolved"
                ))
                .when(ticketDependencyService).validateNoUnresolvedBlockers(100L);

        assertThatThrownBy(() -> ticketService.update(100L, new UpdateTicketRequest(
                null,
                null,
                null,
                null,
                TicketStatus.DONE,
                null,
                null
        )))
                .isInstanceOf(BusinessRuleViolationException.class)
                .hasMessage("Ticket cannot move to DONE while blockers are unresolved");
    }

    @Test
    void softDeleteHidesTicket() {
        Ticket ticket = ticket(TicketStatus.TODO);
        when(ticketRepository.findActiveWithActiveProjectById(100L)).thenReturn(Optional.of(ticket), Optional.empty());

        ticketService.delete(100L);

        verify(ticketRepository).delete(ticket);
        assertThatThrownBy(() -> ticketService.getById(100L))
                .isInstanceOf(NotFoundException.class)
                .hasMessage("Ticket not found");
    }

    @Test
    void createWithManualAssigneeDoesNotTriggerAutoAssignment() {
        Project project = project();
        User reporter = user(1L, "reporter");
        User assignee = user(2L, "assignee");
        when(projectRepository.findById(10L)).thenReturn(Optional.of(project));
        when(userRepository.findById(1L)).thenReturn(Optional.of(reporter));
        when(userRepository.findById(2L)).thenReturn(Optional.of(assignee));
        when(ticketRepository.save(any(Ticket.class))).thenAnswer(invocation -> {
            Ticket ticket = invocation.getArgument(0);
            ticket.setId(100L);
            return ticket;
        });

        var response = ticketService.create(createRequest(2L));

        verify(ticketAssignmentService, never()).autoAssignIfNeeded(any(Ticket.class));
        verify(ticketAssignmentService, never()).recordAutoAssignment(any(Ticket.class));
        assertThat(response.assigneeId()).isEqualTo(2L);
    }

    @Test
    void allowsManualAssigneeWhoIsNotProjectMember() {
        Project project = project();
        User reporter = user(1L, "reporter");
        User assignee = user(2L, "assignee");
        when(projectRepository.findById(10L)).thenReturn(Optional.of(project));
        when(userRepository.findById(1L)).thenReturn(Optional.of(reporter));
        when(userRepository.findById(2L)).thenReturn(Optional.of(assignee));
        when(ticketRepository.save(any(Ticket.class))).thenAnswer(invocation -> {
            Ticket ticket = invocation.getArgument(0);
            ticket.setId(100L);
            return ticket;
        });

        var response = ticketService.create(createRequest(2L));

        assertThat(response.assigneeId()).isEqualTo(2L);
        verify(ticketAssignmentService, never()).autoAssignIfNeeded(any(Ticket.class));
    }

    private static CreateTicketRequest createRequest(Long assigneeId) {
        return new CreateTicketRequest(
                10L,
                assigneeId,
                "Ticket title",
                "Ticket description",
                TicketStatus.TODO,
                TicketType.FEATURE,
                TicketPriority.MEDIUM,
                null
        );
    }

    private static Ticket ticket(TicketStatus status) {
        Ticket ticket = new Ticket();
        ticket.setId(100L);
        ticket.setProject(project());
        ticket.setReporter(user(1L, "reporter"));
        ticket.setTitle("Ticket title");
        ticket.setDescription("Ticket description");
        ticket.setStatus(status);
        ticket.setPriority(TicketPriority.MEDIUM);
        ticket.setType(TicketType.FEATURE);
        ticket.setOverdue(true);
        return ticket;
    }

    private static Project project() {
        Project project = new Project();
        project.setId(10L);
        project.setName("Project");
        project.setKey("PROJECT");
        project.setOwner(user(1L, "reporter"));
        return project;
    }

    private static User user(Long id, String username) {
        User user = new User();
        user.setId(id);
        user.setUsername(username);
        user.setEmail(username + "@example.com");
        user.setFullName(username);
        user.setRole(Role.DEVELOPER);
        return user;
    }
}
