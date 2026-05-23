package com.att.tdp.issueflow.importexport;

import com.att.tdp.issueflow.audit.AuditAction;
import com.att.tdp.issueflow.audit.AuditEventPublisher;
import com.att.tdp.issueflow.audit.AuditableEntityType;
import com.att.tdp.issueflow.security.auth.CurrentUser;
import com.att.tdp.issueflow.ticket.TicketService;
import com.att.tdp.issueflow.ticket.dto.TicketResponse;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TicketExportService {

    private final TicketService ticketService;
    private final TicketCsvService ticketCsvService;
    private final AuditEventPublisher auditEventPublisher;

    public TicketExportService(
            TicketService ticketService,
            TicketCsvService ticketCsvService,
            AuditEventPublisher auditEventPublisher
    ) {
        this.ticketService = ticketService;
        this.ticketCsvService = ticketCsvService;
        this.auditEventPublisher = auditEventPublisher;
    }

    @Transactional
    public byte[] exportTickets(Long projectId, CurrentUser currentUser) {
        List<TicketResponse> tickets = ticketService.getByProject(projectId);
        String csv = ticketCsvService.generate(tickets);
        auditEventPublisher.userAction(
                currentUser == null ? null : currentUser.id(),
                AuditAction.EXPORT_TICKETS,
                AuditableEntityType.PROJECT,
                projectId,
                null,
                "Exported " + tickets.size() + " tickets"
        );
        return csv.getBytes(StandardCharsets.UTF_8);
    }
}
