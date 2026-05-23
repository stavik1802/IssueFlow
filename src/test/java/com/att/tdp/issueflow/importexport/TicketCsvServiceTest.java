package com.att.tdp.issueflow.importexport;

import static org.assertj.core.api.Assertions.assertThat;

import com.att.tdp.issueflow.ticket.TicketPriority;
import com.att.tdp.issueflow.ticket.TicketStatus;
import com.att.tdp.issueflow.ticket.TicketType;
import com.att.tdp.issueflow.ticket.dto.TicketResponse;
import java.io.StringReader;
import java.util.List;
import org.junit.jupiter.api.Test;

class TicketCsvServiceTest {

    private final TicketCsvService ticketCsvService = new TicketCsvService();

    @Test
    void exportsValidCsvWithEscapedValues() {
        String csv = ticketCsvService.generate(List.of(new TicketResponse(
                100L,
                10L,
                1L,
                2L,
                "Fix \"quoted\", title",
                "Description, with comma",
                TicketType.BUG,
                TicketStatus.IN_PROGRESS,
                TicketPriority.HIGH,
                null,
                false,
                null,
                null,
                0L
        )));

        assertThat(csv).startsWith("id,title,description,status,priority,type,assigneeId");
        assertThat(csv).contains("\"Fix \"\"quoted\"\", title\"");
        assertThat(csv).contains("\"Description, with comma\"");
        assertThat(csv).contains("IN_PROGRESS,HIGH,BUG,2");
    }

    @Test
    void parsesQuotedCommas() {
        String csv = """
                id,title,description,status,priority,type,assigneeId
                ,"Fix login, then logout","Body with ""quotes"", and comma",TODO,MEDIUM,BUG,
                """;

        List<TicketCsvService.TicketCsvRow> rows = ticketCsvService.parse(new StringReader(csv));

        assertThat(rows).hasSize(1);
        assertThat(rows.getFirst().rowNumber()).isEqualTo(2);
        assertThat(rows.getFirst().title()).isEqualTo("Fix login, then logout");
        assertThat(rows.getFirst().description()).isEqualTo("Body with \"quotes\", and comma");
    }
}
