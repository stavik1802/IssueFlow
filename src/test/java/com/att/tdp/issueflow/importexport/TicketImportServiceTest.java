package com.att.tdp.issueflow.importexport;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.att.tdp.issueflow.audit.AuditEventPublisher;
import com.att.tdp.issueflow.common.exception.BadRequestException;
import com.att.tdp.issueflow.importexport.dto.TicketImportSummary;
import com.att.tdp.issueflow.project.ProjectRepository;
import com.att.tdp.issueflow.security.auth.CurrentUser;
import com.att.tdp.issueflow.ticket.TicketPriority;
import com.att.tdp.issueflow.ticket.TicketService;
import com.att.tdp.issueflow.ticket.TicketStatus;
import com.att.tdp.issueflow.ticket.TicketType;
import com.att.tdp.issueflow.ticket.dto.CreateTicketRequest;
import com.att.tdp.issueflow.user.Role;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;

@ExtendWith(MockitoExtension.class)
class TicketImportServiceTest {

    @Mock
    private TicketService ticketService;

    @Mock
    private ProjectRepository projectRepository;

    @Mock
    private AuditEventPublisher auditEventPublisher;

    private TicketImportService ticketImportService;

    @BeforeEach
    void setUp() {
        ticketImportService = new TicketImportService(
                new TicketCsvService(),
                ticketService,
                projectRepository,
                auditEventPublisher
        );
    }

    @Test
    void importsValidCsv() {
        when(projectRepository.existsById(10L)).thenReturn(true);
        MockMultipartFile file = csv("""
                id,title,description,status,priority,type,assigneeId
                ,Fix bug,Plain body,TODO,HIGH,BUG,2
                """);

        TicketImportSummary summary = ticketImportService.importTickets(10L, file, currentUser());

        ArgumentCaptor<CreateTicketRequest> requestCaptor = ArgumentCaptor.forClass(CreateTicketRequest.class);
        verify(ticketService).create(requestCaptor.capture());
        CreateTicketRequest request = requestCaptor.getValue();
        assertThat(summary.created()).isEqualTo(1);
        assertThat(summary.failed()).isZero();
        assertThat(request.projectId()).isEqualTo(10L);
        assertThat(request.assigneeId()).isEqualTo(2L);
        assertThat(request.status()).isEqualTo(TicketStatus.TODO);
        assertThat(request.priority()).isEqualTo(TicketPriority.HIGH);
        assertThat(request.type()).isEqualTo(TicketType.BUG);
    }

    @Test
    void importsQuotedCommas() {
        when(projectRepository.existsById(10L)).thenReturn(true);
        MockMultipartFile file = csv("""
                id,title,description,status,priority,type,assigneeId
                ,"Fix login, then logout","Body with ""quotes"", and comma",IN_REVIEW,MEDIUM,FEATURE,
                """);

        TicketImportSummary summary = ticketImportService.importTickets(10L, file, currentUser());

        ArgumentCaptor<CreateTicketRequest> requestCaptor = ArgumentCaptor.forClass(CreateTicketRequest.class);
        verify(ticketService).create(requestCaptor.capture());
        assertThat(summary.created()).isEqualTo(1);
        assertThat(requestCaptor.getValue().title()).isEqualTo("Fix login, then logout");
        assertThat(requestCaptor.getValue().description()).isEqualTo("Body with \"quotes\", and comma");
        assertThat(requestCaptor.getValue().assigneeId()).isNull();
    }

    @Test
    void reportsInvalidEnum() {
        when(projectRepository.existsById(10L)).thenReturn(true);
        MockMultipartFile file = csv("""
                id,title,description,status,priority,type,assigneeId
                ,Fix bug,Plain body,NOT_REAL,HIGH,BUG,
                """);

        TicketImportSummary summary = ticketImportService.importTickets(10L, file, currentUser());

        verify(ticketService, never()).create(any());
        assertThat(summary.created()).isZero();
        assertThat(summary.failed()).isEqualTo(1);
        assertThat(summary.errors().getFirst().row()).isEqualTo(2);
        assertThat(summary.errors().getFirst().message()).isEqualTo("status has invalid value: NOT_REAL");
    }

    @Test
    void allowsPartialFailureSummary() {
        when(projectRepository.existsById(10L)).thenReturn(true);
        MockMultipartFile file = csv("""
                id,title,description,status,priority,type,assigneeId
                ,First,Plain body,TODO,HIGH,BUG,
                ,Second,Plain body,NOT_REAL,HIGH,BUG,
                ,Third,Plain body,DONE,LOW,FEATURE,
                """);

        TicketImportSummary summary = ticketImportService.importTickets(10L, file, currentUser());

        verify(ticketService, org.mockito.Mockito.times(2)).create(any());
        assertThat(summary.created()).isEqualTo(2);
        assertThat(summary.failed()).isEqualTo(1);
        assertThat(summary.errors()).hasSize(1);
        assertThat(summary.errors().getFirst().row()).isEqualTo(3);
    }

    @Test
    void rejectsRowsMissingRequiredTicketFieldsWithoutDefaulting() {
        when(projectRepository.existsById(10L)).thenReturn(true);
        MockMultipartFile file = csv("""
                id,title,description,status,priority,type,assigneeId
                ,Missing description,,TODO,HIGH,BUG,
                ,Missing status,Plain body,,HIGH,BUG,
                ,Missing priority,Plain body,TODO,,BUG,
                ,Missing type,Plain body,TODO,HIGH,,
                ,Valid,Plain body,TODO,HIGH,BUG,
                """);

        TicketImportSummary summary = ticketImportService.importTickets(10L, file, currentUser());

        verify(ticketService, org.mockito.Mockito.times(1)).create(any());
        assertThat(summary.created()).isEqualTo(1);
        assertThat(summary.failed()).isEqualTo(4);
        assertThat(summary.errors())
                .extracting(error -> error.message())
                .containsExactly(
                        "description is required",
                        "status is required",
                        "priority is required",
                        "type is required"
                );
    }

    @Test
    void rejectsFileWhoseContentTypeIsNotCsv() {
        when(projectRepository.existsById(10L)).thenReturn(true);
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "tickets.json",
                "application/json",
                "{}".getBytes()
        );

        assertThatThrownBy(() -> ticketImportService.importTickets(10L, file, currentUser()))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("CSV file type is required");
        verify(ticketService, never()).create(any());
    }

    @Test
    void rejectsFileWhoseExtensionIsNotCsv() {
        when(projectRepository.existsById(10L)).thenReturn(true);
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "tickets.txt",
                "text/csv",
                "id,title,description,status,priority,type,assigneeId\n".getBytes()
        );

        assertThatThrownBy(() -> ticketImportService.importTickets(10L, file, currentUser()))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("CSV file type is required");
        verify(ticketService, never()).create(any());
    }

    private MockMultipartFile csv(String content) {
        return new MockMultipartFile("file", "tickets.csv", "text/csv", content.getBytes());
    }

    private CurrentUser currentUser() {
        return new CurrentUser(1L, "reporter", "reporter@example.com", "Reporter", Role.DEVELOPER);
    }
}
