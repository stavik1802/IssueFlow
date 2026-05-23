package com.att.tdp.issueflow.scheduler;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

@Configuration
@EnableScheduling
public class TicketEscalationScheduler {

    private final TicketEscalationService ticketEscalationService;

    public TicketEscalationScheduler(TicketEscalationService ticketEscalationService) {
        this.ticketEscalationService = ticketEscalationService;
    }

    @Scheduled(fixedDelayString = "${issueflow.scheduler.ticket-escalation-delay:PT15M}")
    public void escalateOverdueTickets() {
        ticketEscalationService.escalateOverdueTickets();
    }
}
