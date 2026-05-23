package com.att.tdp.issueflow.scheduler;

import com.att.tdp.issueflow.audit.AuditAction;
import com.att.tdp.issueflow.audit.AuditEventPublisher;
import com.att.tdp.issueflow.audit.AuditableEntityType;
import com.att.tdp.issueflow.ticket.Ticket;
import com.att.tdp.issueflow.ticket.TicketPriority;
import com.att.tdp.issueflow.ticket.TicketRepository;
import com.att.tdp.issueflow.ticket.TicketStatus;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TicketEscalationService {

    private final TicketRepository ticketRepository;
    private final AuditEventPublisher auditEventPublisher;
    private final Clock clock;

    public TicketEscalationService(
            TicketRepository ticketRepository,
            AuditEventPublisher auditEventPublisher,
            Clock clock
    ) {
        this.ticketRepository = ticketRepository;
        this.auditEventPublisher = auditEventPublisher;
        this.clock = clock;
    }

    @Transactional
    public int escalateOverdueTickets() {
        Instant now = Instant.now(clock);
        List<Ticket> tickets = ticketRepository.findEscalationCandidates(
                now,
                TicketStatus.DONE
        );
        tickets.forEach(this::escalate);
        return tickets.size();
    }

    private void escalate(Ticket ticket) {
        TicketPriority oldPriority = ticket.getPriority();
        TicketPriority newPriority = nextPriority(oldPriority);
        boolean oldOverdue = ticket.isOverdue();
        boolean newOverdue = newPriority == TicketPriority.CRITICAL;
        ticket.setPriority(newPriority);
        ticket.setOverdue(newOverdue);

        auditEventPublisher.systemAction(
                AuditAction.AUTO_ESCALATE,
                AuditableEntityType.TICKET,
                ticket.getId(),
                new EscalationAuditValue(oldPriority, oldOverdue),
                new EscalationAuditValue(newPriority, newOverdue)
        );
    }

    private TicketPriority nextPriority(TicketPriority priority) {
        return switch (priority) {
            case LOW -> TicketPriority.MEDIUM;
            case MEDIUM -> TicketPriority.HIGH;
            case HIGH, CRITICAL -> TicketPriority.CRITICAL;
        };
    }

    private record EscalationAuditValue(TicketPriority priority, boolean overdue) {
    }
}
