package com.att.tdp.issueflow.ticket.dto;

import com.att.tdp.issueflow.ticket.TicketPriority;
import com.att.tdp.issueflow.ticket.TicketStatus;
import com.att.tdp.issueflow.ticket.TicketType;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

@JsonPropertyOrder({"id", "title", "status", "priority", "type", "projectId"})
public record DeletedTicketResponse(
        Long id,
        String title,
        TicketStatus status,
        TicketPriority priority,
        TicketType type,
        Long projectId
) {
    public static DeletedTicketResponse from(TicketResponse ticket) {
        return new DeletedTicketResponse(
                ticket.id(),
                ticket.title(),
                ticket.status(),
                ticket.priority(),
                ticket.type(),
                ticket.projectId()
        );
    }
}
