package com.att.tdp.issueflow.importexport.dto;

public record TicketImportError(
        long row,
        String message
) {
}
