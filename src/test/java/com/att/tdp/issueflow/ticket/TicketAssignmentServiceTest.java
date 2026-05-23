package com.att.tdp.issueflow.ticket;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.att.tdp.issueflow.audit.AuditAction;
import com.att.tdp.issueflow.audit.AuditEventPublisher;
import com.att.tdp.issueflow.audit.AuditableEntityType;
import com.att.tdp.issueflow.project.Project;
import com.att.tdp.issueflow.project.ProjectMember;
import com.att.tdp.issueflow.project.ProjectMemberRepository;
import com.att.tdp.issueflow.user.Role;
import com.att.tdp.issueflow.user.User;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class TicketAssignmentServiceTest {

    @Mock
    private ProjectMemberRepository projectMemberRepository;

    @Mock
    private TicketRepository ticketRepository;

    @Mock
    private AuditEventPublisher auditEventPublisher;

    private TicketAssignmentService ticketAssignmentService;

    @BeforeEach
    void setUp() {
        ticketAssignmentService = new TicketAssignmentService(
                projectMemberRepository,
                ticketRepository,
                auditEventPublisher
        );
    }

    @Test
    void assignsLeastLoadedDeveloper() {
        Project project = project();
        User alice = user(1L, "alice", Role.DEVELOPER, "2026-01-01T00:00:00Z");
        User bob = user(2L, "bob", Role.DEVELOPER, "2026-01-02T00:00:00Z");
        when(projectMemberRepository.findByProjectId(10L)).thenReturn(List.of(member(project, alice), member(project, bob)));
        when(ticketRepository.countOpenTicketsByAssignee(10L)).thenReturn(List.of(count(1L, 3), count(2L, 1)));

        Optional<User> assignee = ticketAssignmentService.chooseAssignee(project);

        assertThat(assignee).containsSame(bob);
    }

    @Test
    void tieBreaksByOldestRegisteredDeveloper() {
        Project project = project();
        User older = user(1L, "older", Role.DEVELOPER, "2026-01-01T00:00:00Z");
        User newer = user(2L, "newer", Role.DEVELOPER, "2026-02-01T00:00:00Z");
        when(projectMemberRepository.findByProjectId(10L)).thenReturn(List.of(member(project, newer), member(project, older)));
        when(ticketRepository.countOpenTicketsByAssignee(10L)).thenReturn(List.of(count(1L, 2), count(2L, 2)));

        Optional<User> assignee = ticketAssignmentService.chooseAssignee(project);

        assertThat(assignee).containsSame(older);
    }

    @Test
    void excludesAdminUsers() {
        Project project = project();
        User admin = user(1L, "admin", Role.ADMIN, "2026-01-01T00:00:00Z");
        User developer = user(2L, "developer", Role.DEVELOPER, "2026-02-01T00:00:00Z");
        when(projectMemberRepository.findByProjectId(10L)).thenReturn(List.of(member(project, admin), member(project, developer)));
        when(ticketRepository.countOpenTicketsByAssignee(10L)).thenReturn(List.of(count(1L, 0), count(2L, 4)));

        Optional<User> assignee = ticketAssignmentService.chooseAssignee(project);

        assertThat(assignee).containsSame(developer);
    }

    @Test
    void returnsEmptyWhenNoDeveloperIsLinkedToProject() {
        Project project = project();
        User admin = user(1L, "admin", Role.ADMIN, "2026-01-01T00:00:00Z");
        when(projectMemberRepository.findByProjectId(10L)).thenReturn(List.of(member(project, admin)));
        when(ticketRepository.countOpenTicketsByAssignee(10L)).thenReturn(List.of(count(1L, 0)));

        Optional<User> assignee = ticketAssignmentService.chooseAssignee(project);

        assertThat(assignee).isEmpty();
    }

    @Test
    void manualAssigneeDoesNotTriggerAutoAssignment() {
        Project project = project();
        User assignee = user(1L, "manual", Role.DEVELOPER, "2026-01-01T00:00:00Z");
        Ticket ticket = new Ticket();
        ticket.setProject(project);
        ticket.setAssignee(assignee);

        ticketAssignmentService.autoAssignIfNeeded(ticket);

        verify(projectMemberRepository, never()).findByProjectId(10L);
        verify(ticketRepository, never()).countOpenTicketsByAssignee(10L);
        assertThat(ticket.getAssignee()).isSameAs(assignee);
    }

    @Test
    void recordsAutoAssignmentAudit() {
        Project project = project();
        User assignee = user(1L, "auto", Role.DEVELOPER, "2026-01-01T00:00:00Z");
        Ticket ticket = new Ticket();
        ticket.setId(100L);
        ticket.setProject(project);
        ticket.setAssignee(assignee);

        ticketAssignmentService.recordAutoAssignment(ticket);

        verify(auditEventPublisher).systemAction(
                org.mockito.Mockito.isNull(),
                eq(AuditAction.AUTO_ASSIGN),
                eq(AuditableEntityType.TICKET),
                eq(100L),
                org.mockito.Mockito.isNull(),
                org.mockito.Mockito.any()
        );
    }

    private static TicketRepository.AssigneeOpenTicketCount count(Long userId, long count) {
        return new TicketRepository.AssigneeOpenTicketCount() {
            @Override
            public Long getUserId() {
                return userId;
            }

            @Override
            public long getOpenTicketCount() {
                return count;
            }
        };
    }

    private static ProjectMember member(Project project, User user) {
        ProjectMember member = new ProjectMember();
        member.setProject(project);
        member.setUser(user);
        return member;
    }

    private static Project project() {
        Project project = new Project();
        project.setId(10L);
        project.setName("Project");
        project.setKey("PROJECT");
        return project;
    }

    private static User user(Long id, String username, Role role, String createdAt) {
        User user = new User();
        user.setId(id);
        user.setUsername(username);
        user.setEmail(username + "@example.com");
        user.setFullName(username);
        user.setRole(role);
        user.setCreatedAt(Instant.parse(createdAt));
        return user;
    }
}
