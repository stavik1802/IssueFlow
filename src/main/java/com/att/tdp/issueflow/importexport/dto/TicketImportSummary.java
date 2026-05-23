package com.att.tdp.issueflow.importexport.dto;

import java.util.List;

public record TicketImportSummary(
        int created,
        int failed,
        List<TicketImportError> errors
) {
}
