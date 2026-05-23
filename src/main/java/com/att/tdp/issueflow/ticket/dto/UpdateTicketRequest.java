package com.att.tdp.issueflow.ticket.dto;

import com.att.tdp.issueflow.ticket.TicketPriority;
import com.att.tdp.issueflow.ticket.TicketStatus;
import jakarta.validation.constraints.Size;
import java.time.Instant;

public record UpdateTicketRequest(
        Long assigneeId,

        @Size(max = 240)
        String title,

        String description,

        TicketPriority priority,

        TicketStatus status,

        Instant dueDate,

        Long version
) {
}
