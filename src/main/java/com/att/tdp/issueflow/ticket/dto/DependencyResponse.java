package com.att.tdp.issueflow.ticket.dto;

import com.att.tdp.issueflow.ticket.TicketStatus;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

@JsonPropertyOrder({"id", "title", "status"})
public record DependencyResponse(
        @JsonIgnore
        Long ticketId,
        @JsonIgnore
        Long blockedBy,
        @JsonIgnore
        String blockedByTitle,
        @JsonIgnore
        TicketStatus blockedByStatus
) {
    @JsonProperty("id")
    public Long id() {
        return blockedBy;
    }

    @JsonProperty("title")
    public String title() {
        return blockedByTitle;
    }

    @JsonProperty("status")
    public TicketStatus status() {
        return blockedByStatus;
    }
}
