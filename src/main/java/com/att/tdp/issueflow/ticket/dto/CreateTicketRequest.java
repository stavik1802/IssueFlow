package com.att.tdp.issueflow.ticket.dto;

import com.att.tdp.issueflow.ticket.TicketPriority;
import com.att.tdp.issueflow.ticket.TicketStatus;
import com.att.tdp.issueflow.ticket.TicketType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;

public record CreateTicketRequest(
        @NotNull
        Long projectId,

        Long assigneeId,

        @NotBlank
        @Size(max = 240)
        String title,

        @NotBlank
        String description,

        @NotNull
        TicketStatus status,

        @NotNull
        TicketType type,

        @NotNull
        TicketPriority priority,

        Instant dueDate
) {
}
