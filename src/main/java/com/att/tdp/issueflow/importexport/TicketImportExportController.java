package com.att.tdp.issueflow.importexport;

import com.att.tdp.issueflow.importexport.dto.TicketImportSummary;
import com.att.tdp.issueflow.security.auth.CurrentUser;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
public class TicketImportExportController {

    private final TicketImportService ticketImportService;
    private final TicketExportService ticketExportService;

    public TicketImportExportController(TicketImportService ticketImportService, TicketExportService ticketExportService) {
        this.ticketImportService = ticketImportService;
        this.ticketExportService = ticketExportService;
    }

    @GetMapping("/tickets/export")
    public ResponseEntity<byte[]> exportTickets(
            @RequestParam Long projectId,
            @AuthenticationPrincipal CurrentUser currentUser
    ) {
        byte[] csv = ticketExportService.exportTickets(projectId, currentUser);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("text/csv"))
                .header(
                        HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment()
                                .filename("tickets-project-" + projectId + ".csv")
                                .build()
                                .toString()
                )
                .body(csv);
    }

    @PostMapping(path = "/tickets/import", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public TicketImportSummary importTickets(
            @RequestParam Long projectId,
            @RequestPart("file") MultipartFile file,
            @AuthenticationPrincipal CurrentUser currentUser
    ) {
        return ticketImportService.importTickets(projectId, file, currentUser);
    }
}
