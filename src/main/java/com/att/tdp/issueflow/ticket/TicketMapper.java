package com.att.tdp.issueflow.ticket;

import com.att.tdp.issueflow.project.Project;
import com.att.tdp.issueflow.ticket.dto.CreateTicketRequest;
import com.att.tdp.issueflow.ticket.dto.TicketResponse;
import com.att.tdp.issueflow.user.User;
import org.springframework.stereotype.Component;

@Component
public class TicketMapper {

    public Ticket toEntity(CreateTicketRequest request, Project project, User reporter, User assignee) {
        Ticket ticket = new Ticket();
        ticket.setProject(project);
        ticket.setReporter(reporter);
        ticket.setAssignee(assignee);
        ticket.setTitle(request.title().trim());
        ticket.setDescription(trimToNull(request.description()));
        ticket.setStatus(request.status());
        ticket.setType(request.type());
        ticket.setPriority(request.priority());
        ticket.setDueAt(request.dueDate());
        return ticket;
    }

    public TicketResponse toResponse(Ticket ticket) {
        return new TicketResponse(
                ticket.getId(),
                ticket.getProject().getId(),
                ticket.getReporter() == null ? null : ticket.getReporter().getId(),
                ticket.getAssignee() == null ? null : ticket.getAssignee().getId(),
                ticket.getTitle(),
                ticket.getDescription(),
                ticket.getType(),
                ticket.getStatus(),
                ticket.getPriority(),
                ticket.getDueAt(),
                ticket.isOverdue(),
                ticket.getResolvedAt(),
                ticket.getDeletedAt(),
                ticket.getVersion()
        );
    }

    String trimToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }
}
