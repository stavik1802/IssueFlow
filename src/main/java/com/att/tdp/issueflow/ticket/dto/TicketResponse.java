package com.att.tdp.issueflow.ticket.dto;

import com.att.tdp.issueflow.ticket.TicketPriority;
import com.att.tdp.issueflow.ticket.TicketStatus;
import com.att.tdp.issueflow.ticket.TicketType;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import java.time.Instant;

@JsonPropertyOrder({"id", "title", "description", "status", "priority", "type", "projectId", "assigneeId", "dueDate", "isOverdue"})
public record TicketResponse(
        Long id,
        Long projectId,
        @JsonIgnore
        Long reporterId,
        Long assigneeId,
        String title,
        String description,
        TicketType type,
        TicketStatus status,
        TicketPriority priority,
        Instant dueDate,
        @JsonProperty("isOverdue")
        boolean overdue,
        @JsonIgnore
        Instant resolvedAt,
        @JsonIgnore
        Instant deletedAt,
        @JsonIgnore
        long version
) {
}
