package com.att.tdp.issueflow;

import static org.assertj.core.api.Assertions.assertThat;

import com.att.tdp.issueflow.project.Project;
import com.att.tdp.issueflow.project.ProjectMember;
import com.att.tdp.issueflow.project.ProjectMemberRepository;
import com.att.tdp.issueflow.project.ProjectMemberRole;
import com.att.tdp.issueflow.project.ProjectRepository;
import com.att.tdp.issueflow.scheduler.TicketEscalationService;
import com.att.tdp.issueflow.security.jwt.JwtTokenService;
import com.att.tdp.issueflow.ticket.Ticket;
import com.att.tdp.issueflow.ticket.TicketPriority;
import com.att.tdp.issueflow.ticket.TicketRepository;
import com.att.tdp.issueflow.ticket.TicketStatus;
import com.att.tdp.issueflow.ticket.TicketType;
import com.att.tdp.issueflow.user.Role;
import com.att.tdp.issueflow.user.User;
import com.att.tdp.issueflow.user.UserRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.springframework.util.FileSystemUtils;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestPropertySource(properties = {
        "spring.jpa.hibernate.ddl-auto=validate",
        "spring.flyway.enabled=true",
        "spring.sql.init.mode=never",
        "spring.jpa.show-sql=false",
        "spring.jpa.properties.hibernate.format_sql=false",
        "spring.servlet.multipart.max-file-size=12MB",
        "spring.servlet.multipart.max-request-size=12MB",
        "logging.level.org.hibernate.SQL=off",
        "logging.level.org.hibernate.orm.jdbc.bind=off"
})
class ApiTraceIT {

    private static final Path UPLOAD_DIR = Path.of("target", "api-trace-uploads").toAbsolutePath().normalize();
    private static final Path TRACE_FILE = Path.of("target", "api-trace", "issueflow-api-trace.json")
            .toAbsolutePath()
            .normalize();
    private static final Clock FIXED_CLOCK = Clock.fixed(Instant.parse("2026-05-20T10:15:30Z"), ZoneOffset.UTC);

    @Container
    @SuppressWarnings("resource")
    private static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("issueflow_trace")
            .withUsername("issueflow")
            .withPassword("issueflow");

    @Autowired
    private TestRestTemplate rest;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ProjectRepository projectRepository;

    @Autowired
    private ProjectMemberRepository projectMemberRepository;

    @Autowired
    private TicketRepository ticketRepository;

    @Autowired
    private TicketEscalationService ticketEscalationService;

    @Autowired
    private JwtTokenService jwtTokenService;

    private final List<Map<String, Object>> trace = new ArrayList<>();
    private int sequence;

    @DynamicPropertySource
    static void postgresProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.datasource.driver-class-name", POSTGRES::getDriverClassName);
        registry.add("issueflow.security.jwt.secret", () -> "trace-secret-key-that-is-long-enough-for-hmac-sha256");
        registry.add("issueflow.security.jwt.expiration", () -> "1h");
        registry.add("issueflow.attachments.upload-directory", () -> UPLOAD_DIR.toString());
    }

    @BeforeEach
    void cleanDatabaseAndFiles() throws IOException {
        jdbcTemplate.execute("""
                truncate table
                    attachments,
                    audit_logs,
                    comments,
                    mentions,
                    project_members,
                    projects,
                    ticket_dependencies,
                    tickets,
                    users
                restart identity cascade
                """);
        FileSystemUtils.deleteRecursively(UPLOAD_DIR);
        Files.createDirectories(UPLOAD_DIR);
        trace.clear();
        sequence = 0;
    }

    @AfterEach
    void writeTrace() throws IOException {
        Files.createDirectories(TRACE_FILE.getParent());
        Map<String, Object> report = orderedMap(
                "database", orderedMap(
                        "type", "PostgreSQL Testcontainers",
                        "jdbcUrl", POSTGRES.getJdbcUrl()
                ),
                "generatedAt", Instant.now().toString(),
                "traceFile", TRACE_FILE.toString(),
                "calls", trace
        );
        objectMapper.writerWithDefaultPrettyPrinter().writeValue(TRACE_FILE.toFile(), report);
    }

    @Test
    void recordsAllApiCallsAndEdgeCasesAgainstRealPostgres() throws Exception {
        String suffix = UUID.randomUUID().toString().substring(0, 8).toLowerCase(Locale.ROOT);

        ResponseEntity<String> publicAdmin = postJson("create admin user before login", "/users", json(
                "username", "admin-" + suffix,
                "email", "admin-" + suffix + "@example.com",
                "fullName", "Admin User",
                "role", "ADMIN"
        ), null, HttpStatus.OK);
        long adminId = body(publicAdmin).get("id").asLong();

        ResponseEntity<String> invalidRole = postJson("create user invalid role edge case", "/users", json(
                "username", "bad-role-" + suffix,
                "email", "bad-role-" + suffix + "@example.com",
                "fullName", "Bad Role",
                "role", "MANAGER"
        ), null, HttpStatus.BAD_REQUEST);
        assertThat(body(invalidRole).get("message").asText()).isEqualTo("role must be one of: ADMIN, DEVELOPER");

        ResponseEntity<String> login = postJson("login admin", "/auth/login", json(
                "username", "admin-" + suffix,
                "password", "secret"
        ), null, HttpStatus.OK);
        String adminToken = body(login).get("accessToken").asText();

        postJson("login wrong password edge case", "/auth/login", json(
                "username", "admin-" + suffix,
                "password", "wrong"
        ), null, HttpStatus.BAD_REQUEST);

        get("protected endpoint without token edge case", "/projects", null, HttpStatus.UNAUTHORIZED);

        JsonNode devA = body(postJson("create developer A", "/users", json(
                "username", "dev-a-" + suffix,
                "email", "dev-a-" + suffix + "@example.com",
                "fullName", "Developer A",
                "role", "DEVELOPER"
        ), adminToken, HttpStatus.OK));
        JsonNode devB = body(postJson("create developer B", "/users", json(
                "username", "dev-b-" + suffix,
                "email", "dev-b-" + suffix + "@example.com",
                "fullName", "Developer B",
                "role", "DEVELOPER"
        ), adminToken, HttpStatus.OK));
        JsonNode disposable = body(postJson("create disposable user", "/users", json(
                "username", "delete-" + suffix,
                "email", "delete-" + suffix + "@example.com",
                "fullName", "Delete Me",
                "role", "DEVELOPER"
        ), adminToken, HttpStatus.OK));

        get("get all users", "/users", adminToken, HttpStatus.OK);
        get("get specific user", "/users/" + devA.get("id").asLong(), adminToken, HttpStatus.OK);
        postJson("duplicate username edge case", "/users", json(
                "username", "dev-a-" + suffix,
                "email", "dupe-" + suffix + "@example.com",
                "fullName", "Duplicate",
                "role", "DEVELOPER"
        ), adminToken, HttpStatus.CONFLICT);
        postJson("update user", "/users/update/" + devB.get("id").asLong(), json(
                "fullName", "Developer B Updated",
                "role", "DEVELOPER"
        ), adminToken, HttpStatus.OK);
        delete("delete user", "/users/" + disposable.get("id").asLong(), adminToken, HttpStatus.OK);
        get("get deleted user edge case", "/users/" + disposable.get("id").asLong(), adminToken, HttpStatus.NOT_FOUND);

        JsonNode tokenUser = body(postJson("create user with same username as auth session", "/users", json(
                "username", "delete-token-" + suffix,
                "email", "delete-token-" + suffix + "@example.com",
                "fullName", "Delete Token User",
                "role", "DEVELOPER"
        ), adminToken, HttpStatus.OK));
        String tokenUserToken = body(postJson("login user before delete", "/auth/login", json(
                "username", tokenUser.get("username").asText(),
                "password", "secret"
        ), null, HttpStatus.OK)).get("accessToken").asText();
        get("auth me before user delete", "/auth/me", tokenUserToken, HttpStatus.OK);
        delete("delete user with same username as auth session", "/users/" + tokenUser.get("id").asLong(), adminToken, HttpStatus.OK);
        get("auth session is invalid after user delete", "/auth/me", tokenUserToken, HttpStatus.UNAUTHORIZED);

        ResponseEntity<String> devLogin = postJson("login developer", "/auth/login", json(
                "username", devA.get("username").asText(),
                "password", "secret"
        ), null, HttpStatus.OK);
        String developerToken = body(devLogin).get("accessToken").asText();
        get("auth me", "/auth/me", developerToken, HttpStatus.OK);
        postJson("logout developer", "/auth/logout", Map.of(), developerToken, HttpStatus.OK);
        get("auth me after logout edge case", "/auth/me", developerToken, HttpStatus.UNAUTHORIZED);

        JsonNode project = body(postJson("create project", "/projects", json(
                "name", "Trace Project",
                "description", "Project created by trace test",
                "ownerId", adminId
        ), adminToken, HttpStatus.OK));
        long projectId = project.get("id").asLong();
        get("get all projects", "/projects", adminToken, HttpStatus.OK);
        get("get project by id", "/projects/" + projectId, adminToken, HttpStatus.OK);
        postJson("create project invalid owner edge case", "/projects", json(
                "name", "Invalid Owner",
                "description", "Should fail",
                "ownerId", 999_999L
        ), adminToken, HttpStatus.NOT_FOUND);
        patchJson("update project", "/projects/" + projectId, json(
                "name", "Trace Project Updated",
                "description", "Updated description"
        ), adminToken, HttpStatus.OK);

        JsonNode adminOnlyProject = body(postJson("create project with no developer members", "/projects", json(
                "name", "Admin Only Project",
                "description", "Used to show no auto-assignment candidate",
                "ownerId", adminId
        ), adminToken, HttpStatus.OK));
        JsonNode unassignedTicket = body(postJson("create ticket without assignee and no developer candidates", "/tickets", json(
                "projectId", adminOnlyProject.get("id").asLong(),
                "title", "No developer candidate",
                "description", "Should stay unassigned",
                "status", "TODO",
                "priority", "LOW",
                "type", "BUG"
        ), adminToken, HttpStatus.OK));
        assertThat(unassignedTicket.get("assigneeId").isNull()).isTrue();
        addProjectMember(adminOnlyProject.get("id").asLong(), devA.get("id").asLong());
        patchJson("update unassigned ticket does not trigger auto-assignment", "/tickets/" + unassignedTicket.get("id").asLong(), json(
                "title", "Still unassigned after update"
        ), adminToken, HttpStatus.OK);
        JsonNode stillUnassignedTicket = body(get("get unassigned ticket after update", "/tickets/" + unassignedTicket.get("id").asLong(), adminToken, HttpStatus.OK));
        assertThat(stillUnassignedTicket.get("assigneeId").isNull()).isTrue();

        addProjectMember(projectId, devA.get("id").asLong());
        addProjectMember(projectId, devB.get("id").asLong());

        JsonNode ticketA = body(postJson("create ticket", "/tickets", json(
                "projectId", projectId,
                "assigneeId", devA.get("id").asLong(),
                "title", "Trace ticket A",
                "description", "A ticket created by the trace test",
                "status", "TODO",
                "priority", "HIGH",
                "type", "BUG",
                "dueDate", "2026-05-21T10:15:30Z"
        ), adminToken, HttpStatus.OK));
        JsonNode ticketB = body(postJson("create second ticket", "/tickets", json(
                "projectId", projectId,
                "assigneeId", devB.get("id").asLong(),
                "title", "Trace ticket B",
                "description", "Used for dependencies and comments",
                "status", "TODO",
                "priority", "MEDIUM",
                "type", "FEATURE"
        ), adminToken, HttpStatus.OK));
        JsonNode autoAssignedTicket = body(postJson("create ticket without assignee auto-assigns least loaded developer", "/tickets", json(
                "projectId", projectId,
                "title", "Auto assigned by workload",
                "description", "Tie should choose oldest developer",
                "status", "TODO",
                "priority", "LOW",
                "type", "TECHNICAL"
        ), adminToken, HttpStatus.OK));
        assertThat(autoAssignedTicket.get("assigneeId").asLong()).isEqualTo(devA.get("id").asLong());
        patchJson("explicit assignee update overrides auto-assignment", "/tickets/" + autoAssignedTicket.get("id").asLong(), json(
                "assigneeId", devB.get("id").asLong()
        ), adminToken, HttpStatus.OK);
        JsonNode overriddenAutoAssignedTicket = body(get("get auto-assigned ticket after explicit override", "/tickets/" + autoAssignedTicket.get("id").asLong(), adminToken, HttpStatus.OK));
        assertThat(overriddenAutoAssignedTicket.get("assigneeId").asLong()).isEqualTo(devB.get("id").asLong());
        get("get audit logs for system auto-assignment", "/audit-logs?action=AUTO_ASSIGN&actor=SYSTEM&size=20", adminToken, HttpStatus.OK);
        get("get tickets for project", "/tickets?projectId=" + projectId, adminToken, HttpStatus.OK);
        get("get ticket by id", "/tickets/" + ticketA.get("id").asLong(), adminToken, HttpStatus.OK);
        postJson("create ticket invalid project edge case", "/tickets", json(
                "projectId", 999_999L,
                "title", "Invalid project",
                "description", "Should fail",
                "status", "TODO",
                "priority", "LOW",
                "type", "BUG"
        ), adminToken, HttpStatus.NOT_FOUND);
        ResponseEntity<String> invalidTicketStatus = postJson("create ticket invalid status edge case", "/tickets", json(
                "projectId", projectId,
                "title", "Invalid status",
                "description", "Should fail with allowed status values",
                "status", "BLOCKED",
                "priority", "LOW",
                "type", "BUG"
        ), adminToken, HttpStatus.BAD_REQUEST);
        assertThat(body(invalidTicketStatus).get("message").asText())
                .isEqualTo("Status must be one of: TODO, IN_PROGRESS, IN_REVIEW, DONE.");
        ResponseEntity<String> invalidTicketPriority = postJson("create ticket invalid priority edge case", "/tickets", json(
                "projectId", projectId,
                "title", "Invalid priority",
                "description", "Should fail with allowed priority values",
                "status", "TODO",
                "priority", "URGENT",
                "type", "BUG"
        ), adminToken, HttpStatus.BAD_REQUEST);
        assertThat(body(invalidTicketPriority).get("message").asText())
                .isEqualTo("Priority must be one of: LOW, MEDIUM, HIGH, CRITICAL.");
        ResponseEntity<String> invalidTicketType = postJson("create ticket invalid type edge case", "/tickets", json(
                "projectId", projectId,
                "title", "Invalid type",
                "description", "Should fail with allowed type values",
                "status", "TODO",
                "priority", "LOW",
                "type", "TASK"
        ), adminToken, HttpStatus.BAD_REQUEST);
        assertThat(body(invalidTicketType).get("message").asText())
                .isEqualTo("Type must be one of: BUG, FEATURE, TECHNICAL.");
        patchJson("update ticket", "/tickets/" + ticketA.get("id").asLong(), json(
                "title", "Trace ticket A updated",
                "priority", "MEDIUM"
        ), adminToken, HttpStatus.OK);
        JsonNode doneTicket = body(postJson("create DONE ticket for immutable update edge case", "/tickets", json(
                "projectId", projectId,
                "title", "Already done",
                "description", "Should not accept later updates",
                "status", "DONE",
                "priority", "LOW",
                "type", "TECHNICAL"
        ), adminToken, HttpStatus.OK));
        ResponseEntity<String> updateDoneTicket = patchJson("update DONE ticket edge case", "/tickets/" + doneTicket.get("id").asLong(), json(
                "title", "Cannot update after done"
        ), adminToken, HttpStatus.UNPROCESSABLE_ENTITY);
        assertThat(body(updateDoneTicket).get("message").asText()).isEqualTo("DONE ticket cannot be updated");

        postJson("add dependency", "/tickets/" + ticketB.get("id").asLong() + "/dependencies", json(
                "blockedBy", ticketA.get("id").asLong()
        ), adminToken, HttpStatus.OK);
        get("list dependencies", "/tickets/" + ticketB.get("id").asLong() + "/dependencies", adminToken, HttpStatus.OK);
        postJson("duplicate dependency edge case", "/tickets/" + ticketB.get("id").asLong() + "/dependencies", json(
                "blockedBy", ticketA.get("id").asLong()
        ), adminToken, HttpStatus.CONFLICT);
        delete("remove dependency", "/tickets/" + ticketB.get("id").asLong() + "/dependencies/" + ticketA.get("id").asLong(), adminToken, HttpStatus.OK);

        JsonNode comment = body(postJson("add comment with mentions", "/tickets/" + ticketB.get("id").asLong() + "/comments", json(
                "authorId", adminId,
                "content", "Please check @" + devA.get("username").asText().toUpperCase(Locale.ROOT)
        ), adminToken, HttpStatus.OK));
        assertThat(comment.get("mentionedUsers").get(0).get("id").asLong()).isEqualTo(devA.get("id").asLong());
        get("get comments for ticket", "/tickets/" + ticketB.get("id").asLong() + "/comments", adminToken, HttpStatus.OK);
        JsonNode devAMentionsBeforeUpdate = body(get("get mentions for originally mentioned user", "/users/" + devA.get("id").asLong() + "/mentions?page=1&pageSize=10", adminToken, HttpStatus.OK));
        assertThat(devAMentionsBeforeUpdate.get("total").asInt()).isEqualTo(1);
        patchJson("update comment", "/tickets/" + ticketB.get("id").asLong() + "/comments/" + comment.get("id").asLong(), json(
                "content", "Updated comment for @" + devB.get("username").asText().toUpperCase(Locale.ROOT)
        ), adminToken, HttpStatus.OK);
        JsonNode commentsAfterMentionUpdate = body(get("get comments after mention re-evaluation", "/tickets/" + ticketB.get("id").asLong() + "/comments", adminToken, HttpStatus.OK));
        assertThat(commentsAfterMentionUpdate.get(0).get("mentionedUsers").get(0).get("id").asLong()).isEqualTo(devB.get("id").asLong());
        JsonNode devBMentionsAfterUpdate = body(get("get mentions for newly mentioned user", "/users/" + devB.get("id").asLong() + "/mentions?page=1&pageSize=10", adminToken, HttpStatus.OK));
        assertThat(devBMentionsAfterUpdate.get("total").asInt()).isEqualTo(1);
        JsonNode devAMentionsAfterUpdate = body(get("get mentions for removed mentioned user", "/users/" + devA.get("id").asLong() + "/mentions?page=1&pageSize=10", adminToken, HttpStatus.OK));
        assertThat(devAMentionsAfterUpdate.get("total").asInt()).isZero();
        postJson("add comment invalid author edge case", "/tickets/" + ticketB.get("id").asLong() + "/comments", json(
                "authorId", 999_999L,
                "content", "Invalid author"
        ), adminToken, HttpStatus.NOT_FOUND);

        ResponseEntity<String> attachment = upload("upload attachment", "/tickets/" + ticketB.get("id").asLong() + "/attachments",
                "note.txt", MediaType.TEXT_PLAIN_VALUE, "hello trace".getBytes(StandardCharsets.UTF_8), adminToken, HttpStatus.OK);
        long attachmentId = body(attachment).get("id").asLong();
        upload("upload blocked file type edge case", "/tickets/" + ticketB.get("id").asLong() + "/attachments",
                "script.sh", "application/x-sh", "rm -rf .".getBytes(StandardCharsets.UTF_8), adminToken, HttpStatus.BAD_REQUEST);

        exportCsv("export tickets csv", "/tickets/export?projectId=" + projectId, adminToken, HttpStatus.OK);
        importCsv("import tickets csv", projectId, """
                id,title,description,status,priority,type,assigneeId
                ,Imported trace ticket,Imported body,TODO,LOW,BUG,
                """, adminToken, HttpStatus.OK);
        importCsv("import tickets csv invalid rows edge case", projectId, """
                id,title,description,status,priority,type,assigneeId
                ,Bad enum,desc,NOT_A_STATUS,LOW,BUG,
                ,,Missing title,TODO,LOW,BUG,
                """, adminToken, HttpStatus.OK);

        JsonNode escalationTicket = body(postJson("create overdue low priority ticket for escalation", "/tickets", json(
                "projectId", projectId,
                "title", "Escalation candidate",
                "description", "Should escalate one level per cycle",
                "status", "TODO",
                "priority", "LOW",
                "type", "BUG",
                "dueDate", "2026-05-19T10:15:30Z"
        ), adminToken, HttpStatus.OK));
        long escalationTicketId = escalationTicket.get("id").asLong();
        recordSystemAction("run escalation cycle 1", "TicketEscalationService.escalateOverdueTickets", ticketEscalationService.escalateOverdueTickets());
        JsonNode afterEscalation1 = body(get("get ticket after escalation cycle 1", "/tickets/" + escalationTicketId, adminToken, HttpStatus.OK));
        assertThat(afterEscalation1.get("priority").asText()).isEqualTo("MEDIUM");
        assertThat(afterEscalation1.get("isOverdue").asBoolean()).isFalse();
        assertThat(afterEscalation1.get("status").asText()).isEqualTo("TODO");
        recordSystemAction("run escalation cycle 2", "TicketEscalationService.escalateOverdueTickets", ticketEscalationService.escalateOverdueTickets());
        JsonNode afterEscalation2 = body(get("get ticket after escalation cycle 2", "/tickets/" + escalationTicketId, adminToken, HttpStatus.OK));
        assertThat(afterEscalation2.get("priority").asText()).isEqualTo("HIGH");
        assertThat(afterEscalation2.get("isOverdue").asBoolean()).isFalse();
        recordSystemAction("run escalation cycle 3", "TicketEscalationService.escalateOverdueTickets", ticketEscalationService.escalateOverdueTickets());
        JsonNode afterEscalation3 = body(get("get ticket after escalation cycle 3", "/tickets/" + escalationTicketId, adminToken, HttpStatus.OK));
        assertThat(afterEscalation3.get("priority").asText()).isEqualTo("CRITICAL");
        assertThat(afterEscalation3.get("isOverdue").asBoolean()).isTrue();
        recordSystemAction("run escalation cycle 4 idempotent critical ticket", "TicketEscalationService.escalateOverdueTickets", ticketEscalationService.escalateOverdueTickets());
        JsonNode afterEscalation4 = body(get("get ticket after idempotent escalation cycle", "/tickets/" + escalationTicketId, adminToken, HttpStatus.OK));
        assertThat(afterEscalation4.get("priority").asText()).isEqualTo("CRITICAL");
        assertThat(afterEscalation4.get("isOverdue").asBoolean()).isTrue();
        patchJson("manual priority change clears overdue flag", "/tickets/" + escalationTicketId, json(
                "priority", "HIGH"
        ), adminToken, HttpStatus.OK);
        JsonNode afterManualPriorityChange = body(get("get ticket after manual priority reset", "/tickets/" + escalationTicketId, adminToken, HttpStatus.OK));
        assertThat(afterManualPriorityChange.get("priority").asText()).isEqualTo("HIGH");
        assertThat(afterManualPriorityChange.get("isOverdue").asBoolean()).isFalse();
        JsonNode noDueDateTicket = body(postJson("create ticket without dueDate ignored by escalation", "/tickets", json(
                "projectId", projectId,
                "title", "No due date escalation ignore",
                "description", "Escalation only applies when dueDate exists",
                "status", "TODO",
                "priority", "LOW",
                "type", "FEATURE"
        ), adminToken, HttpStatus.OK));
        recordSystemAction("run escalation after manual reset and no due date ticket", "TicketEscalationService.escalateOverdueTickets", ticketEscalationService.escalateOverdueTickets());
        JsonNode noDueDateAfterEscalation = body(get("get no dueDate ticket after escalation", "/tickets/" + noDueDateTicket.get("id").asLong(), adminToken, HttpStatus.OK));
        assertThat(noDueDateAfterEscalation.get("priority").asText()).isEqualTo("LOW");

        get("get project workload", "/projects/" + projectId + "/workload", adminToken, HttpStatus.OK);
        get("get audit logs filtered", "/audit-logs?action=CREATE&actor=USER&size=20", adminToken, HttpStatus.OK);

        JsonNode softTicket = body(postJson("create ticket for soft delete", "/tickets", json(
                "projectId", projectId,
                "title", "Soft deleted trace ticket",
                "description", "Will be restored",
                "status", "TODO",
                "priority", "LOW",
                "type", "BUG"
        ), adminToken, HttpStatus.OK));
        delete("soft delete ticket", "/tickets/" + softTicket.get("id").asLong(), adminToken, HttpStatus.OK);
        get("list soft deleted tickets as developer edge case", "/tickets/deleted?projectId=" + projectId,
                jwtTokenService.generateToken(userRepository.findById(devB.get("id").asLong()).orElseThrow()),
                HttpStatus.FORBIDDEN);
        get("list soft deleted tickets", "/tickets/deleted?projectId=" + projectId, adminToken, HttpStatus.OK);
        postJson("restore ticket", "/tickets/" + softTicket.get("id").asLong() + "/restore", Map.of(), adminToken, HttpStatus.OK);

        JsonNode softProject = body(postJson("create project for soft delete", "/projects", json(
                "name", "Soft Delete Project",
                "description", "Will be restored",
                "ownerId", adminId
        ), adminToken, HttpStatus.OK));
        delete("soft delete project", "/projects/" + softProject.get("id").asLong(), adminToken, HttpStatus.OK);
        get("list soft deleted projects", "/projects/deleted", adminToken, HttpStatus.OK);
        postJson("restore project", "/projects/" + softProject.get("id").asLong() + "/restore", Map.of(), adminToken, HttpStatus.OK);

        delete("delete attachment", "/tickets/" + ticketB.get("id").asLong() + "/attachments/" + attachmentId, adminToken, HttpStatus.OK);
        delete("delete comment", "/tickets/" + ticketB.get("id").asLong() + "/comments/" + comment.get("id").asLong(), adminToken, HttpStatus.OK);
        delete("soft delete ticket", "/tickets/" + ticketA.get("id").asLong(), adminToken, HttpStatus.OK);
        delete("soft delete project", "/projects/" + projectId, adminToken, HttpStatus.OK);

        assertTraceContainsOnlyExpectedStatuses();
    }

    private ResponseEntity<String> get(String name, String path, String token, HttpStatus expectedStatus) {
        return exchange(name, HttpMethod.GET, path, null, authHeaders(token), expectedStatus, null);
    }

    private ResponseEntity<String> postJson(String name, String path, Object body, String token, HttpStatus expectedStatus) {
        return exchange(name, HttpMethod.POST, path, body, jsonHeaders(token), expectedStatus, body);
    }

    private ResponseEntity<String> patchJson(String name, String path, Object body, String token, HttpStatus expectedStatus) {
        return exchange(name, HttpMethod.PATCH, path, body, jsonHeaders(token), expectedStatus, body);
    }

    private ResponseEntity<String> delete(String name, String path, String token, HttpStatus expectedStatus) {
        return exchange(name, HttpMethod.DELETE, path, null, authHeaders(token), expectedStatus, null);
    }

    private ResponseEntity<String> exportCsv(String name, String path, String token, HttpStatus expectedStatus) {
        return exchange(name, HttpMethod.GET, path, null, authHeaders(token), expectedStatus, null);
    }

    private ResponseEntity<String> upload(
            String name,
            String path,
            String filename,
            String contentType,
            byte[] bytes,
            String token,
            HttpStatus expectedStatus
    ) {
        MultiValueMap<String, Object> multipartBody = new LinkedMultiValueMap<>();
        multipartBody.add("file", filePart(filename, contentType, bytes));
        Map<String, Object> traceRequest = orderedMap(
                "multipart", true,
                "file", orderedMap(
                        "filename", filename,
                        "contentType", contentType,
                        "sizeBytes", bytes.length
                )
        );
        return exchange(name, HttpMethod.POST, path, multipartBody, multipartHeaders(token), expectedStatus, traceRequest);
    }

    private ResponseEntity<String> importCsv(
            String name,
            long projectId,
            String csv,
            String token,
            HttpStatus expectedStatus
    ) {
        MultiValueMap<String, Object> multipartBody = new LinkedMultiValueMap<>();
        multipartBody.add("projectId", String.valueOf(projectId));
        multipartBody.add("file", filePart("tickets.csv", "text/csv", csv.getBytes(StandardCharsets.UTF_8)));
        Map<String, Object> traceRequest = orderedMap(
                "multipart", true,
                "projectId", projectId,
                "file", orderedMap(
                        "filename", "tickets.csv",
                        "contentType", "text/csv",
                        "content", csv
                )
        );
        return exchange(name, HttpMethod.POST, "/tickets/import?projectId=" + projectId, multipartBody,
                multipartHeaders(token), expectedStatus, traceRequest);
    }

    private ResponseEntity<String> exchange(
            String name,
            HttpMethod method,
            String path,
            Object actualBody,
            HttpHeaders headers,
            HttpStatus expectedStatus,
            Object traceRequestBody
    ) {
        ResponseEntity<String> response = rest.exchange(path, method, new HttpEntity<>(actualBody, headers), String.class);
        recordTrace(name, method, path, headers, traceRequestBody, expectedStatus, response);
        assertThat(response.getStatusCode()).isEqualTo(expectedStatus);
        return response;
    }

    private void recordTrace(
            String name,
            HttpMethod method,
            String path,
            HttpHeaders requestHeaders,
            Object requestBody,
            HttpStatus expectedStatus,
            ResponseEntity<String> response
    ) {
        trace.add(orderedMap(
                "sequence", ++sequence,
                "name", name,
                "request", orderedMap(
                        "method", method.name(),
                        "path", path,
                        "headers", traceHeaders(requestHeaders),
                        "body", requestBody
                ),
                "expectedStatus", expectedStatus.value(),
                "response", orderedMap(
                        "status", response.getStatusCode().value(),
                        "headers", traceHeaders(response.getHeaders()),
                        "body", parseBody(response.getBody())
                )
        ));
    }

    private void recordSystemAction(String name, String action, Object result) {
        trace.add(orderedMap(
                "sequence", ++sequence,
                "name", name,
                "request", orderedMap(
                        "method", "SYSTEM",
                        "path", action,
                        "headers", Map.of(),
                        "body", null
                ),
                "expectedStatus", 200,
                "response", orderedMap(
                        "status", 200,
                        "headers", Map.of(),
                        "body", orderedMap("result", result)
                )
        ));
    }

    private Object parseBody(String body) {
        if (body == null || body.isBlank()) {
            return null;
        }
        try {
            return objectMapper.readTree(body);
        } catch (IOException exception) {
            return body;
        }
    }

    private Map<String, Object> traceHeaders(HttpHeaders headers) {
        Map<String, Object> result = new LinkedHashMap<>();
        headers.forEach((key, values) -> {
            if (HttpHeaders.AUTHORIZATION.equalsIgnoreCase(key)) {
                result.put(key, List.of("Bearer <redacted>"));
            } else if (Set.of(HttpHeaders.CONTENT_TYPE, HttpHeaders.ACCEPT, HttpHeaders.CONTENT_LENGTH).contains(key)) {
                result.put(key, values);
            }
        });
        return result;
    }

    private JsonNode body(ResponseEntity<String> response) throws IOException {
        assertThat(response.getBody()).isNotBlank();
        return objectMapper.readTree(response.getBody());
    }

    private HttpEntity<ByteArrayResource> filePart(String filename, String contentType, byte[] bytes) {
        HttpHeaders partHeaders = new HttpHeaders();
        partHeaders.setContentType(MediaType.parseMediaType(contentType));
        return new HttpEntity<>(new ByteArrayResource(bytes) {
            @Override
            public String getFilename() {
                return filename;
            }
        }, partHeaders);
    }

    private HttpHeaders jsonHeaders(String token) {
        HttpHeaders headers = authHeaders(token);
        headers.setContentType(MediaType.APPLICATION_JSON);
        return headers;
    }

    private HttpHeaders multipartHeaders(String token) {
        HttpHeaders headers = authHeaders(token);
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);
        return headers;
    }

    private HttpHeaders authHeaders(String token) {
        HttpHeaders headers = new HttpHeaders();
        if (token != null) {
            headers.setBearerAuth(token);
        }
        return headers;
    }

    private void addProjectMember(long projectId, long userId) {
        Project project = projectRepository.findById(projectId).orElseThrow();
        User user = userRepository.findById(userId).orElseThrow();
        ProjectMember member = new ProjectMember();
        member.setProject(project);
        member.setUser(user);
        member.setRole(ProjectMemberRole.MEMBER);
        projectMemberRepository.saveAndFlush(member);
    }

    private void assertTraceContainsOnlyExpectedStatuses() {
        assertThat(trace)
                .allSatisfy(entry -> {
                    int expectedStatus = (Integer) entry.get("expectedStatus");
                    @SuppressWarnings("unchecked")
                    Map<String, Object> response = (Map<String, Object>) entry.get("response");
                    assertThat(response.get("status")).isEqualTo(expectedStatus);
                });
    }

    private Map<String, Object> json(Object... keyValues) {
        return orderedMap(keyValues);
    }

    private Map<String, Object> orderedMap(Object... keyValues) {
        Map<String, Object> body = new LinkedHashMap<>();
        for (int index = 0; index < keyValues.length; index += 2) {
            body.put((String) keyValues[index], keyValues[index + 1]);
        }
        return body;
    }

    @TestConfiguration
    static class DeterministicTimeConfig {

        @Bean
        @Primary
        Clock fixedTestClock() {
            return FIXED_CLOCK;
        }
    }
}
