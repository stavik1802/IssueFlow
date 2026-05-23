package com.att.tdp.issueflow.scheduler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.att.tdp.issueflow.audit.AuditAction;
import com.att.tdp.issueflow.audit.AuditEventPublisher;
import com.att.tdp.issueflow.audit.AuditableEntityType;
import com.att.tdp.issueflow.ticket.Ticket;
import com.att.tdp.issueflow.ticket.TicketPriority;
import com.att.tdp.issueflow.ticket.TicketRepository;
import com.att.tdp.issueflow.ticket.TicketStatus;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class TicketEscalationServiceTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-05-20T10:15:30Z"), ZoneOffset.UTC);

    @Mock
    private TicketRepository ticketRepository;

    @Mock
    private AuditEventPublisher auditEventPublisher;

    private TicketEscalationService ticketEscalationService;

    @BeforeEach
    void setUp() {
        ticketEscalationService = new TicketEscalationService(ticketRepository, auditEventPublisher, CLOCK);
    }

    @Test
    void lowEscalatesToMedium() {
        Ticket ticket = ticket(100L, TicketPriority.LOW, TicketStatus.TODO);
        when(ticketRepository.findEscalationCandidates(
                Instant.now(CLOCK),
                TicketStatus.DONE
        )).thenReturn(List.of(ticket));

        int escalated = ticketEscalationService.escalateOverdueTickets();

        assertThat(escalated).isEqualTo(1);
        assertThat(ticket.getPriority()).isEqualTo(TicketPriority.MEDIUM);
        assertThat(ticket.isOverdue()).isFalse();
        verify(auditEventPublisher).systemAction(
                org.mockito.Mockito.eq(AuditAction.AUTO_ESCALATE),
                org.mockito.Mockito.eq(AuditableEntityType.TICKET),
                org.mockito.Mockito.eq(100L),
                org.mockito.Mockito.any(),
                org.mockito.Mockito.any()
        );
    }

    @Test
    void highEscalatesToCritical() {
        Ticket ticket = ticket(100L, TicketPriority.HIGH, TicketStatus.IN_REVIEW);
        when(ticketRepository.findEscalationCandidates(
                Instant.now(CLOCK),
                TicketStatus.DONE
        )).thenReturn(List.of(ticket));

        ticketEscalationService.escalateOverdueTickets();

        assertThat(ticket.getPriority()).isEqualTo(TicketPriority.CRITICAL);
        assertThat(ticket.isOverdue()).isTrue();
        assertThat(ticket.getStatus()).isEqualTo(TicketStatus.IN_REVIEW);
    }

    @Test
    void mediumEscalatesToHigh() {
        Ticket ticket = ticket(100L, TicketPriority.MEDIUM, TicketStatus.IN_PROGRESS);
        when(ticketRepository.findEscalationCandidates(
                Instant.now(CLOCK),
                TicketStatus.DONE
        )).thenReturn(List.of(ticket));

        ticketEscalationService.escalateOverdueTickets();

        assertThat(ticket.getPriority()).isEqualTo(TicketPriority.HIGH);
        assertThat(ticket.isOverdue()).isFalse();
        assertThat(ticket.getStatus()).isEqualTo(TicketStatus.IN_PROGRESS);
    }

    @Test
    void criticalOverdueSetsOverdue() {
        Ticket ticket = ticket(100L, TicketPriority.CRITICAL, TicketStatus.IN_PROGRESS);
        when(ticketRepository.findEscalationCandidates(
                Instant.now(CLOCK),
                TicketStatus.DONE
        )).thenReturn(List.of(ticket));

        ticketEscalationService.escalateOverdueTickets();

        assertThat(ticket.getPriority()).isEqualTo(TicketPriority.CRITICAL);
        assertThat(ticket.isOverdue()).isTrue();
    }

    @Test
    void doneTicketsAreIgnoredByQuery() {
        when(ticketRepository.findEscalationCandidates(
                Instant.now(CLOCK),
                TicketStatus.DONE
        )).thenReturn(List.of());

        int escalated = ticketEscalationService.escalateOverdueTickets();

        assertThat(escalated).isZero();
        verify(ticketRepository).findEscalationCandidates(
                Instant.now(CLOCK),
                TicketStatus.DONE
        );
        verify(auditEventPublisher, never()).systemAction(
                org.mockito.Mockito.any(),
                org.mockito.Mockito.any(),
                org.mockito.Mockito.any(),
                org.mockito.Mockito.any(),
                org.mockito.Mockito.any()
        );
    }

    @Test
    void ticketsWithoutDueDateAreIgnoredByQuery() {
        when(ticketRepository.findEscalationCandidates(
                Instant.now(CLOCK),
                TicketStatus.DONE
        )).thenReturn(List.of());

        int escalated = ticketEscalationService.escalateOverdueTickets();

        assertThat(escalated).isZero();
        verify(ticketRepository).findEscalationCandidates(
                Instant.now(CLOCK),
                TicketStatus.DONE
        );
    }

    @Test
    void repeatedEscalationPromotesUntilCriticalThenBecomesIdempotent() {
        Ticket ticket = ticket(100L, TicketPriority.LOW, TicketStatus.TODO);
        when(ticketRepository.findEscalationCandidates(
                Instant.now(CLOCK),
                TicketStatus.DONE
        )).thenReturn(List.of(ticket), List.of(ticket), List.of(ticket), List.of());

        int firstRun = ticketEscalationService.escalateOverdueTickets();
        int secondRun = ticketEscalationService.escalateOverdueTickets();
        int thirdRun = ticketEscalationService.escalateOverdueTickets();
        int fourthRun = ticketEscalationService.escalateOverdueTickets();

        assertThat(firstRun).isEqualTo(1);
        assertThat(secondRun).isEqualTo(1);
        assertThat(thirdRun).isEqualTo(1);
        assertThat(fourthRun).isZero();
        assertThat(ticket.getPriority()).isEqualTo(TicketPriority.CRITICAL);
        assertThat(ticket.isOverdue()).isTrue();
        verify(auditEventPublisher, times(3)).systemAction(
                org.mockito.Mockito.eq(AuditAction.AUTO_ESCALATE),
                org.mockito.Mockito.eq(AuditableEntityType.TICKET),
                org.mockito.Mockito.eq(100L),
                org.mockito.Mockito.any(),
                org.mockito.Mockito.any()
        );
    }

    private static Ticket ticket(Long id, TicketPriority priority, TicketStatus status) {
        Ticket ticket = new Ticket();
        ticket.setId(id);
        ticket.setTitle("Ticket " + id);
        ticket.setPriority(priority);
        ticket.setStatus(status);
        ticket.setDueAt(Instant.parse("2026-05-19T10:15:30Z"));
        ticket.setOverdue(false);
        return ticket;
    }
}
