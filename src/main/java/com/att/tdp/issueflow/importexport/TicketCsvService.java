package com.att.tdp.issueflow.importexport;

import com.att.tdp.issueflow.common.exception.BadRequestException;
import com.att.tdp.issueflow.ticket.dto.TicketResponse;
import java.io.IOException;
import java.io.Reader;
import java.io.StringWriter;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.List;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVPrinter;
import org.apache.commons.csv.CSVRecord;
import org.springframework.stereotype.Service;

@Service
public class TicketCsvService {

    private static final String ID = "id";
    private static final String TITLE = "title";
    private static final String DESCRIPTION = "description";
    private static final String STATUS = "status";
    private static final String PRIORITY = "priority";
    private static final String TYPE = "type";
    private static final String ASSIGNEE_ID = "assigneeId";
    private static final String[] HEADERS = {ID, TITLE, DESCRIPTION, STATUS, PRIORITY, TYPE, ASSIGNEE_ID};

    public String generate(List<TicketResponse> tickets) {
        try {
            StringWriter writer = new StringWriter();
            try (CSVPrinter printer = new CSVPrinter(writer, CSVFormat.DEFAULT.builder()
                    .setHeader(HEADERS)
                    .build())) {
                for (TicketResponse ticket : tickets) {
                    printer.printRecord(
                            ticket.id(),
                            ticket.title(),
                            ticket.description(),
                            ticket.status(),
                            ticket.priority(),
                            ticket.type(),
                            ticket.assigneeId()
                    );
                }
            }
            return writer.toString();
        } catch (IOException exception) {
            throw new UncheckedIOException("Failed to generate ticket CSV", exception);
        }
    }

    public List<TicketCsvRow> parse(Reader reader) {
        try (CSVParser parser = CSVFormat.DEFAULT.builder()
                .setHeader()
                .setSkipHeaderRecord(true)
                .setTrim(true)
                .setIgnoreSurroundingSpaces(true)
                .build()
                .parse(reader)) {
            validateHeaders(parser);
            List<TicketCsvRow> rows = new ArrayList<>();
            for (CSVRecord record : parser) {
                rows.add(new TicketCsvRow(
                        record.getRecordNumber() + 1,
                        value(record, TITLE),
                        value(record, DESCRIPTION),
                        value(record, STATUS),
                        value(record, PRIORITY),
                        value(record, TYPE),
                        value(record, ASSIGNEE_ID)
                ));
            }
            return rows;
        } catch (IllegalArgumentException exception) {
            throw new BadRequestException("CSV is missing required ticket columns", exception);
        } catch (IOException exception) {
            throw new BadRequestException("CSV file could not be read", exception);
        }
    }

    private void validateHeaders(CSVParser parser) {
        for (String header : HEADERS) {
            if (!parser.getHeaderMap().containsKey(header)) {
                throw new BadRequestException("CSV is missing required column: " + header);
            }
        }
    }

    private String value(CSVRecord record, String header) {
        String value = record.get(header);
        return value == null || value.isBlank() ? null : value.trim();
    }

    public record TicketCsvRow(
            long rowNumber,
            String title,
            String description,
            String status,
            String priority,
            String type,
            String assigneeId
    ) {
    }
}
