package com.att.tdp.issueflow.ticket;

import com.att.tdp.issueflow.common.exception.BusinessRuleViolationException;
import org.springframework.stereotype.Component;

@Component
public class TicketLifecyclePolicy {

    public void validateUpdateAllowed(Ticket ticket) {
        if (ticket.getStatus() == TicketStatus.DONE) {
            throw new BusinessRuleViolationException("DONE ticket cannot be updated");
        }
    }

    public void validateStatusTransition(TicketStatus currentStatus, TicketStatus requestedStatus) {
        if (requestedStatus == null || requestedStatus == currentStatus) {
            return;
        }
        if (requestedStatus.ordinal() < currentStatus.ordinal()) {
            throw new BusinessRuleViolationException("Ticket status cannot move backward");
        }
        if (requestedStatus.ordinal() > currentStatus.ordinal() + 1) {
            throw new BusinessRuleViolationException("Ticket status can only move one step forward");
        }
    }
}
