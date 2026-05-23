package com.att.tdp.issueflow.ticket;

import com.att.tdp.issueflow.audit.AuditAction;
import com.att.tdp.issueflow.audit.AuditEventPublisher;
import com.att.tdp.issueflow.audit.AuditableEntityType;
import com.att.tdp.issueflow.project.Project;
import com.att.tdp.issueflow.project.ProjectMemberRepository;
import com.att.tdp.issueflow.user.Role;
import com.att.tdp.issueflow.user.User;
import java.time.Instant;
import java.util.Comparator;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TicketAssignmentService {

    private final ProjectMemberRepository projectMemberRepository;
    private final TicketRepository ticketRepository;
    private final AuditEventPublisher auditEventPublisher;

    public TicketAssignmentService(
            ProjectMemberRepository projectMemberRepository,
            TicketRepository ticketRepository,
            AuditEventPublisher auditEventPublisher
    ) {
        this.projectMemberRepository = projectMemberRepository;
        this.ticketRepository = ticketRepository;
        this.auditEventPublisher = auditEventPublisher;
    }

    @Transactional(readOnly = true)
    public Optional<User> chooseAssignee(Project project) {
        Map<Long, Long> openTicketCounts = ticketRepository.countOpenTicketsByAssignee(project.getId()).stream()
                .collect(Collectors.toMap(
                        TicketRepository.AssigneeOpenTicketCount::getUserId,
                        TicketRepository.AssigneeOpenTicketCount::getOpenTicketCount,
                        Long::sum
                ));

        return projectMemberRepository.findByProjectId(project.getId()).stream()
                .map(member -> member.getUser())
                .filter(user -> user.getRole() == Role.DEVELOPER)
                .min(Comparator
                        .comparingLong((User user) -> openTicketCounts.getOrDefault(user.getId(), 0L))
                        .thenComparing(user -> registeredAt(user), Comparator.nullsLast(Comparator.naturalOrder()))
                        .thenComparing(User::getId));
    }

    @Transactional
    public void autoAssignIfNeeded(Ticket ticket) {
        if (ticket.getAssignee() != null) {
            return;
        }
        chooseAssignee(ticket.getProject()).ifPresent(ticket::setAssignee);
    }

    @Transactional
    public void recordAutoAssignment(Ticket ticket) {
        recordAutoAssignment(ticket, null);
    }

    @Transactional
    public void recordAutoAssignment(Ticket ticket, Long actorId) {
        if (ticket.getAssignee() == null) {
            return;
        }
        auditEventPublisher.systemAction(
                actorId,
                AuditAction.AUTO_ASSIGN,
                AuditableEntityType.TICKET,
                ticket.getId(),
                null,
                new AutoAssignmentAuditValue(ticket.getAssignee().getId(), ticket.getAssignee().getUsername())
        );
    }

    private Instant registeredAt(User user) {
        return user.getCreatedAt();
    }

    private record AutoAssignmentAuditValue(Long assigneeId, String username) {
    }
}
