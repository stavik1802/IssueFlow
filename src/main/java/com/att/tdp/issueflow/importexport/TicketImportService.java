package com.att.tdp.issueflow.importexport;

import com.att.tdp.issueflow.audit.AuditAction;
import com.att.tdp.issueflow.audit.AuditEventPublisher;
import com.att.tdp.issueflow.audit.AuditableEntityType;
import com.att.tdp.issueflow.common.exception.BadRequestException;
import com.att.tdp.issueflow.common.exception.ForbiddenException;
import com.att.tdp.issueflow.common.exception.NotFoundException;
import com.att.tdp.issueflow.importexport.dto.TicketImportError;
import com.att.tdp.issueflow.importexport.dto.TicketImportSummary;
import com.att.tdp.issueflow.project.ProjectRepository;
import com.att.tdp.issueflow.security.auth.CurrentUser;
import com.att.tdp.issueflow.ticket.TicketPriority;
import com.att.tdp.issueflow.ticket.TicketService;
import com.att.tdp.issueflow.ticket.TicketStatus;
import com.att.tdp.issueflow.ticket.TicketType;
import com.att.tdp.issueflow.ticket.dto.CreateTicketRequest;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

@Service
public class TicketImportService {

    private final TicketCsvService ticketCsvService;
    private final TicketService ticketService;
    private final ProjectRepository projectRepository;
    private final AuditEventPublisher auditEventPublisher;

    public TicketImportService(
            TicketCsvService ticketCsvService,
            TicketService ticketService,
            ProjectRepository projectRepository,
            AuditEventPublisher auditEventPublisher
    ) {
        this.ticketCsvService = ticketCsvService;
        this.ticketService = ticketService;
        this.projectRepository = projectRepository;
        this.auditEventPublisher = auditEventPublisher;
    }

    public TicketImportSummary importTickets(Long projectId, MultipartFile file, CurrentUser currentUser) {
        requireAuthenticated(currentUser);
        if (!projectRepository.existsById(projectId)) {
            throw new NotFoundException("Project not found");
        }
        if (file == null || file.isEmpty()) {
            throw new BadRequestException("CSV file is required");
        }
        validateCsvFile(file);

        List<TicketCsvService.TicketCsvRow> rows = parse(file);
        List<TicketImportError> errors = new ArrayList<>();
        int created = 0;

        for (TicketCsvService.TicketCsvRow row : rows) {
            try {
                ticketService.create(toCreateTicketRequest(projectId, row));
                created++;
            } catch (RuntimeException exception) {
                errors.add(new TicketImportError(row.rowNumber(), exception.getMessage()));
            }
        }

        TicketImportSummary summary = new TicketImportSummary(created, errors.size(), List.copyOf(errors));
        auditEventPublisher.userAction(
                currentUser.id(),
                AuditAction.IMPORT_TICKETS,
                AuditableEntityType.TICKET,
                projectId,
                null,
                summary
        );
        return summary;
    }

    private void validateCsvFile(MultipartFile file) {
        String contentType = normalizeContentType(file.getContentType());
        if (!"text/csv".equals(contentType)) {
            throw new BadRequestException("CSV file type is required");
        }
        String extension = StringUtils.getFilenameExtension(file.getOriginalFilename());
        if (extension == null || !"csv".equals(extension.toLowerCase(Locale.ROOT))) {
            throw new BadRequestException("CSV file type is required");
        }
    }

    private String normalizeContentType(String contentType) {
        if (contentType == null) {
            return "";
        }
        int parametersStart = contentType.indexOf(';');
        String mediaType = parametersStart < 0 ? contentType : contentType.substring(0, parametersStart);
        return mediaType.trim().toLowerCase(Locale.ROOT);
    }

    private List<TicketCsvService.TicketCsvRow> parse(MultipartFile file) {
        try {
            return ticketCsvService.parse(new BufferedReader(new InputStreamReader(file.getInputStream(), StandardCharsets.UTF_8)));
        } catch (IOException exception) {
            throw new BadRequestException("CSV file could not be read", exception);
        }
    }

    private CreateTicketRequest toCreateTicketRequest(Long projectId, TicketCsvService.TicketCsvRow row) {
        if (row.title() == null) {
            throw new BadRequestException("title is required");
        }
        if (row.description() == null) {
            throw new BadRequestException("description is required");
        }
        TicketStatus status = parseRequiredEnum(TicketStatus.class, row.status(), "status");
        TicketType type = parseRequiredEnum(TicketType.class, row.type(), "type");
        TicketPriority priority = parseRequiredEnum(TicketPriority.class, row.priority(), "priority");
        return new CreateTicketRequest(
                projectId,
                parseAssigneeId(row.assigneeId()),
                row.title(),
                row.description(),
                status,
                type,
                priority,
                null
        );
    }

    private Long parseAssigneeId(String assigneeId) {
        if (assigneeId == null) {
            return null;
        }
        try {
            return Long.valueOf(assigneeId);
        } catch (NumberFormatException exception) {
            throw new BadRequestException("assigneeId must be a valid number");
        }
    }

    private <T extends Enum<T>> T parseRequiredEnum(Class<T> enumType, String value, String fieldName) {
        if (value == null) {
            throw new BadRequestException(fieldName + " is required");
        }
        try {
            return Enum.valueOf(enumType, value);
        } catch (IllegalArgumentException exception) {
            throw new BadRequestException(fieldName + " has invalid value: " + value);
        }
    }

    private void requireAuthenticated(CurrentUser currentUser) {
        if (currentUser == null || currentUser.id() == null) {
            throw new ForbiddenException("Authentication is required");
        }
    }
}
