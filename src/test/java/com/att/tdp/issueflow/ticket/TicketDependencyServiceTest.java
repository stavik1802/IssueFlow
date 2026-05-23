package com.att.tdp.issueflow.ticket;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.att.tdp.issueflow.audit.AuditEventPublisher;
import com.att.tdp.issueflow.common.exception.BusinessRuleViolationException;
import com.att.tdp.issueflow.common.exception.ConflictException;
import com.att.tdp.issueflow.common.exception.NotFoundException;
import com.att.tdp.issueflow.project.Project;
import com.att.tdp.issueflow.ticket.dto.AddDependencyRequest;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class TicketDependencyServiceTest {

    @Mock
    private TicketDependencyRepository ticketDependencyRepository;

    @Mock
    private TicketRepository ticketRepository;

    @Mock
    private AuditEventPublisher auditEventPublisher;

    private TicketDependencyService ticketDependencyService;

    @BeforeEach
    void setUp() {
        ticketDependencyService = new TicketDependencyService(
                ticketDependencyRepository,
                ticketRepository,
                auditEventPublisher
        );
    }

    @Test
    void addsDependency() {
        Ticket ticket = ticket(100L, project(10L), TicketStatus.TODO);
        Ticket blocker = ticket(42L, project(10L), TicketStatus.IN_PROGRESS);
        when(ticketRepository.findActiveWithActiveProjectById(100L)).thenReturn(Optional.of(ticket));
        when(ticketRepository.findActiveWithActiveProjectById(42L)).thenReturn(Optional.of(blocker));
        when(ticketDependencyRepository.existsByTicketIdAndDependsOnTicketId(100L, 42L)).thenReturn(false);
        when(ticketDependencyRepository.findByTicketIdOrderByCreatedAtDesc(42L)).thenReturn(List.of());
        when(ticketDependencyRepository.save(org.mockito.ArgumentMatchers.any(TicketDependency.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        var response = ticketDependencyService.addDependency(100L, new AddDependencyRequest(42L));

        assertThat(response.ticketId()).isEqualTo(100L);
        assertThat(response.blockedBy()).isEqualTo(42L);
        assertThat(response.blockedByTitle()).isEqualTo("Ticket 42");
        assertThat(response.blockedByStatus()).isEqualTo(TicketStatus.IN_PROGRESS);
    }

    @Test
    void rejectsMissingTicket() {
        when(ticketRepository.findActiveWithActiveProjectById(100L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> ticketDependencyService.addDependency(100L, new AddDependencyRequest(42L)))
                .isInstanceOf(NotFoundException.class)
                .hasMessage("Ticket not found");
    }

    @Test
    void rejectsDifferentProject() {
        Ticket ticket = ticket(100L, project(10L), TicketStatus.TODO);
        Ticket blocker = ticket(42L, project(20L), TicketStatus.TODO);
        when(ticketRepository.findActiveWithActiveProjectById(100L)).thenReturn(Optional.of(ticket));
        when(ticketRepository.findActiveWithActiveProjectById(42L)).thenReturn(Optional.of(blocker));

        assertThatThrownBy(() -> ticketDependencyService.addDependency(100L, new AddDependencyRequest(42L)))
                .isInstanceOf(BusinessRuleViolationException.class)
                .hasMessage("Dependent tickets must belong to the same project");
    }

    @Test
    void rejectsSelfDependency() {
        Ticket ticket = ticket(100L, project(10L), TicketStatus.TODO);
        when(ticketRepository.findActiveWithActiveProjectById(100L)).thenReturn(Optional.of(ticket));

        assertThatThrownBy(() -> ticketDependencyService.addDependency(100L, new AddDependencyRequest(100L)))
                .isInstanceOf(BusinessRuleViolationException.class)
                .hasMessage("Ticket cannot depend on itself");
    }

    @Test
    void rejectsDuplicateDependency() {
        Ticket ticket = ticket(100L, project(10L), TicketStatus.TODO);
        Ticket blocker = ticket(42L, project(10L), TicketStatus.TODO);
        when(ticketRepository.findActiveWithActiveProjectById(100L)).thenReturn(Optional.of(ticket));
        when(ticketRepository.findActiveWithActiveProjectById(42L)).thenReturn(Optional.of(blocker));
        when(ticketDependencyRepository.existsByTicketIdAndDependsOnTicketId(100L, 42L)).thenReturn(true);

        assertThatThrownBy(() -> ticketDependencyService.addDependency(100L, new AddDependencyRequest(42L)))
                .isInstanceOf(ConflictException.class)
                .hasMessage("Ticket dependency already exists");
    }

    @Test
    void preventsDoneIfBlockerNotDone() {
        when(ticketDependencyRepository.existsUnresolvedBlocker(100L)).thenReturn(true);

        assertThatThrownBy(() -> ticketDependencyService.validateNoUnresolvedBlockers(100L))
                .isInstanceOf(BusinessRuleViolationException.class)
                .hasMessage("Ticket cannot move to DONE while blockers are unresolved");
    }

    @Test
    void deletesExistingDependencyAndAuditsRemoval() {
        when(ticketRepository.existsActiveWithActiveProjectById(100L)).thenReturn(true);
        when(ticketRepository.existsActiveWithActiveProjectById(42L)).thenReturn(true);
        when(ticketDependencyRepository.existsByTicketIdAndDependsOnTicketId(100L, 42L)).thenReturn(true);

        ticketDependencyService.deleteDependency(100L, 42L);

        verify(ticketDependencyRepository).deleteByTicketIdAndDependsOnTicketId(100L, 42L);
        verify(auditEventPublisher).userAction(
                org.mockito.ArgumentMatchers.isNull(),
                org.mockito.ArgumentMatchers.eq(com.att.tdp.issueflow.audit.AuditAction.REMOVE_DEPENDENCY),
                org.mockito.ArgumentMatchers.eq(com.att.tdp.issueflow.audit.AuditableEntityType.TICKET_DEPENDENCY),
                org.mockito.ArgumentMatchers.eq(100L),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.isNull()
        );
    }

    @Test
    void rejectsMissingDependencyWithoutAudit() {
        when(ticketRepository.existsActiveWithActiveProjectById(100L)).thenReturn(true);
        when(ticketRepository.existsActiveWithActiveProjectById(42L)).thenReturn(true);
        when(ticketDependencyRepository.existsByTicketIdAndDependsOnTicketId(100L, 42L)).thenReturn(false);

        assertThatThrownBy(() -> ticketDependencyService.deleteDependency(100L, 42L))
                .isInstanceOf(NotFoundException.class)
                .hasMessage("Ticket not found");

        verify(ticketDependencyRepository, never()).deleteByTicketIdAndDependsOnTicketId(100L, 42L);
        verify(auditEventPublisher, never()).userAction(
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.eq(com.att.tdp.issueflow.audit.AuditAction.REMOVE_DEPENDENCY),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any()
        );
    }

    private static Ticket ticket(Long id, Project project, TicketStatus status) {
        Ticket ticket = new Ticket();
        ticket.setId(id);
        ticket.setProject(project);
        ticket.setTitle("Ticket " + id);
        ticket.setStatus(status);
        ticket.setPriority(TicketPriority.MEDIUM);
        ticket.setType(TicketType.FEATURE);
        return ticket;
    }

    private static Project project(Long id) {
        Project project = new Project();
        project.setId(id);
        project.setName("Project " + id);
        project.setKey("PROJECT-" + id);
        return project;
    }
}
