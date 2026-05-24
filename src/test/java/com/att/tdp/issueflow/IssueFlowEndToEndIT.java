package com.att.tdp.issueflow;

import static org.assertj.core.api.Assertions.assertThat;

import com.att.tdp.issueflow.project.Project;
import com.att.tdp.issueflow.project.ProjectMember;
import com.att.tdp.issueflow.project.ProjectMemberRepository;
import com.att.tdp.issueflow.project.ProjectMemberRole;
import com.att.tdp.issueflow.project.ProjectRepository;
import com.att.tdp.issueflow.scheduler.TicketEscalationService;
import com.att.tdp.issueflow.security.jwt.JwtTokenService;
import com.att.tdp.issueflow.user.User;
import com.att.tdp.issueflow.user.UserRepository;
import com.fasterxml.jackson.databind.JsonNode;
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
import java.util.UUID;
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

/**
 * Run this end-to-end test with:
 *
 * <pre>
 * .\mvnw.cmd test -Dtest=IssueFlowEndToEndIT
 * </pre>
 *
 * It uses a real PostgreSQL Testcontainer, the real random-port web server,
 * real Spring Security JWT filters, controllers, services, repositories, and
 * Hibernate mappings. It intentionally does not use Mockito or @MockBean.
 */
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
class IssueFlowEndToEndIT {

    private static final Path UPLOAD_DIR = Path.of("target", "e2e-uploads").toAbsolutePath().normalize();
    private static final Clock FIXED_CLOCK = Clock.fixed(Instant.parse("2026-05-20T10:15:30Z"), ZoneOffset.UTC);
    private static final String LOGIN_PASSWORD = "secret";

    @Container
    @SuppressWarnings("resource")
    private static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("issueflow_e2e")
            .withUsername("issueflow")
            .withPassword("issueflow");

    @Autowired
    private TestRestTemplate rest;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private ProjectRepository projectRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private JwtTokenService jwtTokenService;

    @Autowired
    private ProjectMemberRepository projectMemberRepository;

    @Autowired
    private TicketEscalationService ticketEscalationService;

    @DynamicPropertySource
    static void postgresProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.datasource.driver-class-name", POSTGRES::getDriverClassName);
        registry.add("issueflow.security.jwt.secret", () -> "e2e-secret-key-that-is-long-enough-for-hmac-sha256");
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
    }

    @Test
    void completeIssueFlowWorkflowUsesRealHttpSecurityJpaAndPostgreSql() {
        String suffix = UUID.randomUUID().toString().substring(0, 8).toLowerCase(Locale.ROOT);

        // 1-3. Users and authentication.
        JsonNode admin = seedUser("admin-" + suffix, "admin-" + suffix + "@example.com", "Admin User", "ADMIN");
        assertThat(postJson("/users", json(
                "username", "unauth-" + suffix,
                "email", "unauth-" + suffix + "@example.com",
                "fullName", "Unauthenticated User",
                "role", "DEVELOPER"
        ), null).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        String adminToken = login(admin.get("username").asText(), LOGIN_PASSWORD);
        JsonNode developerA = createUser("deva-" + suffix, "deva-" + suffix + "@example.com", "Developer A", "DEVELOPER", adminToken);
        JsonNode developerB = createUser("devb-" + suffix, "devb-" + suffix + "@example.com", "Developer B", "DEVELOPER", adminToken);
        JsonNode disposableUser = createUser("delete-" + suffix, "delete-" + suffix + "@example.com", "Delete Me", "DEVELOPER", adminToken);

        assertThat(admin.has("passwordHash")).isFalse();
        assertThat(postJson("/users", json(
                "username", admin.get("username").asText(),
                "email", "other-" + suffix + "@example.com",
                "fullName", "Duplicate Username",
                "role", "DEVELOPER"
        ), adminToken).getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(postJson("/users", json(
                "username", "other-" + suffix,
                "email", developerA.get("email").asText(),
                "fullName", "Duplicate Email",
                "role", "DEVELOPER"
        ), adminToken).getStatusCode()).isEqualTo(HttpStatus.CONFLICT);

        String developerToken = login(developerA.get("username").asText(), LOGIN_PASSWORD);
        String developerBToken = jwtTokenService.generateToken(userRepository.findById(developerB.get("id").asLong()).orElseThrow());
        assertThat(postJson("/auth/login", json("username", admin.get("username").asText(), "password", "wrong"), null)
                .getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(getJson("/projects", null).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        JsonNode me = expectOk(getJson("/auth/me", developerToken));
        assertThat(me.get("id").asLong()).isEqualTo(developerA.get("id").asLong());
        assertThat(me.get("username").asText()).isEqualTo(developerA.get("username").asText());

        expectOk(getJson("/users/" + developerA.get("id").asLong(), adminToken));
        JsonNode users = expectOk(getJson("/users", adminToken));
        assertThat(ids(users)).contains(
                admin.get("id").asLong(),
                developerA.get("id").asLong(),
                developerB.get("id").asLong()
        );
        assertThat(postJson(
                "/users/update/" + developerB.get("id").asLong(),
                json("fullName", "Developer B Updated", "role", "DEVELOPER"),
                adminToken
        ).getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode updatedDeveloperB = expectOk(getJson("/users/" + developerB.get("id").asLong(), adminToken));
        assertThat(updatedDeveloperB.get("fullName").asText()).isEqualTo("Developer B Updated");

        assertThat(delete("/users/" + disposableUser.get("id").asLong(), adminToken).getStatusCode())
                .isEqualTo(HttpStatus.OK);
        assertThat(getJson("/users/" + disposableUser.get("id").asLong(), adminToken).getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND);

        assertThat(postJson("/auth/logout", Map.of(), developerToken).getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(getJson("/auth/me", developerToken).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        developerToken = login(developerA.get("username").asText(), LOGIN_PASSWORD);

        // 4-5. Projects, project membership setup, and workload.
        JsonNode project = expectCreated(postJson("/projects", json(
                "name", "IssueFlow E2E",
                "description", "End-to-end project",
                "ownerId", admin.get("id").asLong()
        ), adminToken));
        long projectId = project.get("id").asLong();
        assertThat(expectOk(getJson("/projects/" + projectId, adminToken)).get("name").asText())
                .isEqualTo("IssueFlow E2E");
        assertThat(ids(expectOk(getJson("/projects", adminToken)))).contains(projectId);
        assertThat(patchJson(
                "/projects/" + projectId,
                json("name", "IssueFlow E2E Updated", "description", "Updated description"),
                adminToken
        ).getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode updatedProject = expectOk(getJson("/projects/" + projectId, adminToken));
        assertThat(updatedProject.get("description").asText()).isEqualTo("Updated description");
        assertThat(patchJson(
                "/projects/" + projectId,
                json("description", "Description-only update"),
                adminToken
        ).getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode descriptionOnlyProjectUpdate = expectOk(getJson("/projects/" + projectId, adminToken));
        assertThat(descriptionOnlyProjectUpdate.get("name").asText()).isEqualTo("IssueFlow E2E Updated");
        assertThat(descriptionOnlyProjectUpdate.get("description").asText()).isEqualTo("Description-only update");
        assertThat(postJson("/projects", json(
                "name", "Invalid Owner",
                "description", "Should fail",
                "ownerId", 999_999L
        ), adminToken).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);

        // There is no HTTP membership endpoint, so this test uses real repositories only
        // to create the project membership state needed by workload and auto-assignment.
        addProjectMember(projectId, developerA.get("id").asLong());
        addProjectMember(projectId, developerB.get("id").asLong());

        // 6. Tickets: create, read, list, update, lifecycle, validation, and auto-assignment.
        JsonNode manualTicketA1 = createTicket(adminToken, json(
                "projectId", projectId,
                "assigneeId", developerA.get("id").asLong(),
                "title", "Manual ticket A1",
                "description", "Assigned manually to developer A",
                "type", "BUG",
                "priority", "MEDIUM"
        ));
        JsonNode manualTicketA2 = createTicket(adminToken, json(
                "projectId", projectId,
                "assigneeId", developerA.get("id").asLong(),
                "title", "Manual ticket A2",
                "description", "Assigned manually to developer A",
                "type", "FEATURE",
                "priority", "LOW"
        ));
        JsonNode autoAssignedTicket = createTicket(adminToken, json(
                "projectId", projectId,
                "title", "Auto assigned ticket",
                "description", "Should go to least-loaded developer",
                "type", "TECHNICAL",
                "priority", "LOW"
        ));
        assertThat(autoAssignedTicket.get("assigneeId").asLong()).isEqualTo(developerB.get("id").asLong());

        JsonNode workload = expectOk(getJson("/projects/" + projectId + "/workload", adminToken));
        assertThat(openTicketCount(workload, developerB.get("id").asLong())).isEqualTo(1);
        assertThat(openTicketCount(workload, developerA.get("id").asLong())).isEqualTo(2);

        JsonNode fetchedTicket = expectOk(getJson("/tickets/" + manualTicketA1.get("id").asLong(), adminToken));
        assertThat(fetchedTicket.get("title").asText()).isEqualTo("Manual ticket A1");
        assertThat(ids(expectOk(getJson("/tickets?projectId=" + projectId, adminToken))))
                .contains(manualTicketA1.get("id").asLong(), autoAssignedTicket.get("id").asLong());

        JsonNode updatedTicket = updateTicket(adminToken, manualTicketA1, json(
                "title", "Manual ticket A1 updated",
                "description", "Updated through HTTP"
        ));
        assertThat(updatedTicket.get("title").asText()).isEqualTo("Manual ticket A1 updated");
        updatedTicket = updateTicket(adminToken, updatedTicket, json("assigneeId", developerB.get("id").asLong()));
        assertThat(updatedTicket.get("assigneeId").asLong()).isEqualTo(developerB.get("id").asLong());

        assertThat(postJson("/tickets", json(
                "projectId", 999_999L,
                "title", "Invalid project",
                "description", "Should fail",
                "status", "TODO",
                "type", "FEATURE",
                "priority", "MEDIUM"
        ), adminToken).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(postJson("/tickets", json(
                "projectId", projectId,
                "assigneeId", 999_999L,
                "title", "Invalid assignee",
                "description", "Should fail",
                "status", "TODO",
                "type", "FEATURE",
                "priority", "MEDIUM"
        ), adminToken).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        JsonNode nonMember = createUser("nonmember-" + suffix, "nonmember-" + suffix + "@example.com", "Non Member", "DEVELOPER", adminToken);
        JsonNode nonMemberTicket = createTicket(adminToken, json(
                "projectId", projectId,
                "assigneeId", nonMember.get("id").asLong(),
                "title", "Non-member assignee",
                "description", "Only user existence is required",
                "status", "TODO",
                "type", "BUG",
                "priority", "LOW"
        ));
        assertThat(nonMemberTicket.get("assigneeId").asLong()).isEqualTo(nonMember.get("id").asLong());

        JsonNode lifecycleTicket = createTicket(adminToken, json(
                "projectId", projectId,
                "title", "Lifecycle ticket",
                "description", "Moves forward",
                "priority", "LOW",
                "type", "FEATURE"
        ));
        lifecycleTicket = updateTicket(adminToken, lifecycleTicket, json("status", "IN_PROGRESS"));
        lifecycleTicket = updateTicket(adminToken, lifecycleTicket, json("status", "IN_REVIEW"));
        lifecycleTicket = updateTicket(adminToken, lifecycleTicket, json("status", "DONE"));
        assertThat(lifecycleTicket.get("status").asText()).isEqualTo("DONE");
        assertThat(patchJson(
                "/tickets/" + lifecycleTicket.get("id").asLong(),
                json("title", "Cannot update done"),
                adminToken
        ).getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);

        JsonNode backwardTicket = createTicket(adminToken, json(
                "projectId", projectId,
                "title", "Backward ticket",
                "description", "Should reject backward status"
        ));
        backwardTicket = updateTicket(adminToken, backwardTicket, json("status", "IN_PROGRESS"));
        assertThat(patchJson(
                "/tickets/" + backwardTicket.get("id").asLong(),
                json("status", "TODO"),
                adminToken
        ).getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);

        JsonNode priorityTicket = createTicket(adminToken, json(
                "projectId", projectId,
                "title", "Priority reset ticket",
                "description", "Manual priority clears overdue",
                "priority", "HIGH",
                "dueDate", "2026-05-19T10:15:30Z"
        ));
        assertThat(ticketEscalationService.escalateOverdueTickets()).isGreaterThanOrEqualTo(1);
        priorityTicket = expectOk(getJson("/tickets/" + priorityTicket.get("id").asLong(), adminToken));
        assertThat(priorityTicket.get("isOverdue").asBoolean()).isTrue();
        priorityTicket = updateTicket(adminToken, priorityTicket, json(
                "priority", "HIGH",
                "dueDate", "2026-05-21T10:15:30Z"
        ));
        assertThat(priorityTicket.get("priority").asText()).isEqualTo("HIGH");
        assertThat(priorityTicket.get("isOverdue").asBoolean()).isFalse();

        // 8. Ticket dependencies.
        JsonNode blocker = createTicket(adminToken, json(
                "projectId", projectId,
                "title", "Blocker ticket",
                "description", "Must be resolved first"
        ));
        JsonNode blocked = createTicket(adminToken, json(
                "projectId", projectId,
                "title", "Blocked ticket",
                "description", "Cannot finish while blocker is open"
        ));
        assertThat(postJson(
                "/tickets/" + blocked.get("id").asLong() + "/dependencies",
                json("blockedBy", blocker.get("id").asLong()),
                adminToken
        ).getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode dependencies = expectOk(getJson("/tickets/" + blocked.get("id").asLong() + "/dependencies", adminToken));
        assertThat(dependencies).hasSize(1);
        assertThat(postJson(
                "/tickets/" + blocked.get("id").asLong() + "/dependencies",
                json("blockedBy", blocker.get("id").asLong()),
                adminToken
        ).getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(postJson(
                "/tickets/" + blocked.get("id").asLong() + "/dependencies",
                json("blockedBy", blocked.get("id").asLong()),
                adminToken
        ).getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);

        JsonNode secondProject = expectCreated(postJson("/projects", json(
                "name", "Other E2E Project",
                "description", "Used for dependency validation",
                "ownerId", admin.get("id").asLong()
        ), adminToken));
        JsonNode otherProjectTicket = createTicket(adminToken, json(
                "projectId", secondProject.get("id").asLong(),
                "title", "Other project blocker",
                "description", "Cannot block across projects"
        ));
        assertThat(postJson(
                "/tickets/" + blocked.get("id").asLong() + "/dependencies",
                json("blockedBy", otherProjectTicket.get("id").asLong()),
                adminToken
        ).getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);

        blocked = updateTicket(adminToken, blocked, json("status", "IN_PROGRESS"));
        blocked = updateTicket(adminToken, blocked, json("status", "IN_REVIEW"));
        JsonNode blockedBeforeDone = expectOk(getJson("/tickets/" + blocked.get("id").asLong(), adminToken));
        assertThat(patchJson(
                "/tickets/" + blockedBeforeDone.get("id").asLong(),
                json("status", "DONE"),
                adminToken
        ).getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        blocker = updateTicket(adminToken, blocker, json("status", "IN_PROGRESS"));
        blocker = updateTicket(adminToken, blocker, json("status", "IN_REVIEW"));
        blocker = updateTicket(adminToken, blocker, json("status", "DONE"));
        blocked = updateTicket(adminToken, blocked, json("status", "DONE"));
        assertThat(blocked.get("status").asText()).isEqualTo("DONE");
        assertThat(delete(
                "/tickets/" + blocked.get("id").asLong() + "/dependencies/" + blocker.get("id").asLong(),
                adminToken
        ).getStatusCode()).isEqualTo(HttpStatus.OK);

        // 9-10. Comments and mentions.
        assertThat(postJson(
                "/tickets/999999/comments",
                json("content", "Invalid ticket", "authorId", admin.get("id").asLong()),
                adminToken
        ).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(postJson(
                "/tickets/" + manualTicketA2.get("id").asLong() + "/comments",
                json("content", "Invalid author", "authorId", 999_999L),
                adminToken
        ).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);

        String devAUsername = developerA.get("username").asText();
        String devBUsername = developerB.get("username").asText();
        JsonNode mentionComment = expectCreated(postJson(
                "/tickets/" + manualTicketA2.get("id").asLong() + "/comments",
                json("content", "Ping @" + devAUsername.toUpperCase(Locale.ROOT) + " and @" + devBUsername + " and @" + devAUsername,
                        "authorId", admin.get("id").asLong()),
                adminToken
        ));
        assertThat(mentionComment.get("mentionedUsers")).hasSize(2);
        JsonNode comments = expectOk(getJson("/tickets/" + manualTicketA2.get("id").asLong() + "/comments", adminToken));
        assertThat(ids(comments)).contains(mentionComment.get("id").asLong());

        assertThat(patchJson(
                "/tickets/" + manualTicketA2.get("id").asLong() + "/comments/" + mentionComment.get("id").asLong(),
                json("content", "Now only @" + devBUsername),
                adminToken
        ).getStatusCode()).isEqualTo(HttpStatus.OK);
        mentionComment = findById(
                expectOk(getJson("/tickets/" + manualTicketA2.get("id").asLong() + "/comments", adminToken)),
                mentionComment.get("id").asLong()
        );
        assertThat(mentionComment.get("mentionedUsers")).hasSize(1);
        assertThat(mentionComment.get("mentionedUsers").get(0).get("id").asLong()).isEqualTo(developerB.get("id").asLong());

        JsonNode newestMention = expectCreated(postJson(
                "/tickets/" + manualTicketA2.get("id").asLong() + "/comments",
                json("content", "Newest mention @" + devBUsername, "authorId", admin.get("id").asLong()),
                adminToken
        ));
        JsonNode devBMentions = expectOk(getJson("/users/" + developerB.get("id").asLong() + "/mentions", adminToken));
        assertThat(devBMentions.get("data").get(0).get("id").asLong()).isEqualTo(newestMention.get("id").asLong());
        assertThat(devBMentions.get("total").asInt()).isGreaterThanOrEqualTo(2);
        assertThat(devBMentions.get("page").asInt()).isEqualTo(1);

        JsonNode commentToDelete = expectCreated(postJson(
                "/tickets/" + manualTicketA2.get("id").asLong() + "/comments",
                json("content", "Delete this comment", "authorId", admin.get("id").asLong()),
                adminToken
        ));
        assertThat(delete(
                "/tickets/" + manualTicketA2.get("id").asLong() + "/comments/" + commentToDelete.get("id").asLong(),
                adminToken
        ).getStatusCode())
                .isEqualTo(HttpStatus.OK);

        // 12. Attachments.
        JsonNode attachment = expectCreated(upload(
                "/tickets/" + manualTicketA2.get("id").asLong() + "/attachments",
                "note.txt",
                MediaType.TEXT_PLAIN_VALUE,
                "hello attachment".getBytes(StandardCharsets.UTF_8),
                adminToken
        ));
        assertThat(attachment.get("contentType").asText()).isEqualTo(MediaType.TEXT_PLAIN_VALUE);
        JsonNode pngAttachment = expectCreated(upload(
                "/tickets/" + manualTicketA2.get("id").asLong() + "/attachments",
                "../pixel.png",
                MediaType.IMAGE_PNG_VALUE,
                new byte[]{(byte) 0x89, 'P', 'N', 'G', 13, 10, 26, 10},
                adminToken
        ));
        assertThat(pngAttachment.get("filename").asText()).isEqualTo("pixel.png");
        assertThat(upload(
                "/tickets/" + manualTicketA2.get("id").asLong() + "/attachments",
                "large.txt",
                MediaType.TEXT_PLAIN_VALUE,
                new byte[(10 * 1024 * 1024) + 1],
                adminToken
        ).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(upload(
                "/tickets/" + manualTicketA2.get("id").asLong() + "/attachments",
                "script.sh",
                "application/x-sh",
                "rm -rf .".getBytes(StandardCharsets.UTF_8),
                adminToken
        ).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(delete(
                "/tickets/" + manualTicketA2.get("id").asLong() + "/attachments/" + attachment.get("id").asLong(),
                adminToken
        ).getStatusCode())
                .isEqualTo(HttpStatus.OK);

        // 13. CSV export and import.
        JsonNode csvTicket = createTicket(adminToken, json(
                "projectId", projectId,
                "title", "CSV ticket",
                "description", "Text with, comma and \"quote\"",
                "type", "BUG",
                "priority", "MEDIUM"
        ));
        ResponseEntity<String> exportedCsv = rest.exchange(
                "/tickets/export?projectId=" + projectId,
                HttpMethod.GET,
                new HttpEntity<>(authHeaders(adminToken)),
                String.class
        );
        assertThat(exportedCsv.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(exportedCsv.getBody()).contains("id,title,description,status,priority,type,assigneeId");
        assertThat(exportedCsv.getBody()).contains("\"Text with, comma and \"\"quote\"\"\"");
        assertThat(exportedCsv.getBody()).contains(String.valueOf(csvTicket.get("id").asLong()));

        JsonNode importSummary = expectOk(importCsv(projectId, """
                id,title,description,status,priority,type,assigneeId
                ,Imported ticket,"Imported, with comma",TODO,LOW,BUG,
                """, adminToken));
        assertThat(importSummary.get("created").asInt()).isEqualTo(1);
        assertThat(importSummary.get("failed").asInt()).isZero();
        JsonNode invalidImportSummary = expectOk(importCsv(projectId, """
                id,title,description,status,priority,type,assigneeId
                ,Bad enum,desc,NOT_A_STATUS,LOW,BUG,
                ,,Missing title,TODO,LOW,BUG,
                """, adminToken));
        assertThat(invalidImportSummary.get("created").asInt()).isZero();
        assertThat(invalidImportSummary.get("failed").asInt()).isEqualTo(2);
        assertThat(invalidImportSummary.get("errors")).hasSize(2);

        // 14. Soft delete and restore for tickets and projects.
        JsonNode softDeleteTicket = createTicket(adminToken, json(
                "projectId", projectId,
                "title", "Soft delete ticket",
                "description", "Should be hidden from normal reads"
        ));
        long softDeleteTicketId = softDeleteTicket.get("id").asLong();
        assertThat(delete("/tickets/" + softDeleteTicketId, adminToken).getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(getJson("/tickets/" + softDeleteTicketId, adminToken).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(ids(expectOk(getJson("/tickets?projectId=" + projectId, adminToken)))).doesNotContain(softDeleteTicketId);
        assertThat(getJson("/tickets/deleted?projectId=" + projectId, developerBToken).getStatusCode())
                .isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(ids(expectOk(getJson("/tickets/deleted?projectId=" + projectId, adminToken))))
                .contains(softDeleteTicketId);
        assertThat(postJson("/tickets/" + softDeleteTicketId + "/restore", Map.of(), adminToken).getStatusCode())
                .isEqualTo(HttpStatus.OK);
        assertThat(expectOk(getJson("/tickets/" + softDeleteTicketId, adminToken)).get("id").asLong())
                .isEqualTo(softDeleteTicketId);

        JsonNode projectToDelete = expectCreated(postJson("/projects", json(
                "name", "Project To Soft Delete",
                "description", "Should be restored",
                "ownerId", admin.get("id").asLong()
        ), adminToken));
        long deletedProjectId = projectToDelete.get("id").asLong();
        assertThat(delete("/projects/" + deletedProjectId, adminToken).getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(getJson("/projects/" + deletedProjectId, adminToken).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(ids(expectOk(getJson("/projects", adminToken)))).doesNotContain(deletedProjectId);
        assertThat(getJson("/projects/deleted", developerBToken).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(ids(expectOk(getJson("/projects/deleted", adminToken)))).contains(deletedProjectId);
        assertThat(postJson("/projects/" + deletedProjectId + "/restore", Map.of(), adminToken).getStatusCode())
                .isEqualTo(HttpStatus.OK);
        assertThat(expectOk(getJson("/projects/" + deletedProjectId, adminToken)).get("id").asLong())
                .isEqualTo(deletedProjectId);

        // 15-16. Audit and scheduler/escalation. There is no HTTP endpoint to trigger
        // escalation, so this invokes the real service as the last resort.
        JsonNode escalationTicket = createTicket(adminToken, json(
                "projectId", projectId,
                "title", "Repeated escalation ticket",
                "description", "Escalates one priority per run",
                "priority", "LOW",
                "dueDate", "2026-05-19T10:15:30Z"
        ));
        assertThat(ticketEscalationService.escalateOverdueTickets()).isEqualTo(1);
        escalationTicket = expectOk(getJson("/tickets/" + escalationTicket.get("id").asLong(), adminToken));
        assertThat(escalationTicket.get("priority").asText()).isEqualTo("MEDIUM");
        assertThat(escalationTicket.get("isOverdue").asBoolean()).isFalse();

        assertThat(ticketEscalationService.escalateOverdueTickets()).isEqualTo(1);
        escalationTicket = expectOk(getJson("/tickets/" + escalationTicket.get("id").asLong(), adminToken));
        assertThat(escalationTicket.get("priority").asText()).isEqualTo("HIGH");
        assertThat(escalationTicket.get("isOverdue").asBoolean()).isFalse();

        assertThat(ticketEscalationService.escalateOverdueTickets()).isEqualTo(1);
        escalationTicket = expectOk(getJson("/tickets/" + escalationTicket.get("id").asLong(), adminToken));
        assertThat(escalationTicket.get("priority").asText()).isEqualTo("CRITICAL");
        assertThat(escalationTicket.get("isOverdue").asBoolean()).isTrue();

        assertThat(ticketEscalationService.escalateOverdueTickets()).isZero();
        escalationTicket = expectOk(getJson("/tickets/" + escalationTicket.get("id").asLong(), adminToken));
        assertThat(escalationTicket.get("status").asText()).isEqualTo("TODO");

        JsonNode donePastDue = createTicket(adminToken, json(
                "projectId", projectId,
                "title", "Done past due",
                "description", "Escalation should ignore DONE",
                "status", "DONE",
                "priority", "LOW",
                "dueDate", "2026-05-19T10:15:30Z"
        ));
        JsonNode withoutDueDate = createTicket(adminToken, json(
                "projectId", projectId,
                "title", "No due date",
                "description", "Escalation should ignore missing due date",
                "priority", "LOW"
        ));
        int ignoredEscalations = ticketEscalationService.escalateOverdueTickets();
        assertThat(ignoredEscalations).isZero();
        assertThat(expectOk(getJson("/tickets/" + donePastDue.get("id").asLong(), adminToken)).get("priority").asText())
                .isEqualTo("LOW");
        assertThat(expectOk(getJson("/tickets/" + withoutDueDate.get("id").asLong(), adminToken)).get("priority").asText())
                .isEqualTo("LOW");

        JsonNode auditLogs = expectOk(getJson("/audit-logs?size=200", adminToken));
        assertThat(actions(auditLogs)).contains(
                "CREATE",
                "UPDATE",
                "DELETE",
                "RESTORE",
                "ADD_DEPENDENCY",
                "REMOVE_DEPENDENCY",
                "UPLOAD_ATTACHMENT",
                "DELETE_ATTACHMENT",
                "IMPORT_TICKETS",
                "EXPORT_TICKETS",
                "AUTO_ASSIGN",
                "AUTO_ESCALATE"
        );
        JsonNode autoAssignAudit = expectOk(getJson("/audit-logs?action=AUTO_ASSIGN&size=20", adminToken))
                .get(0);
        assertThat(autoAssignAudit.get("actor").asText()).isEqualTo("SYSTEM");
        assertThat(autoAssignAudit.get("performedBy").asLong()).isEqualTo(admin.get("id").asLong());
        assertThat(autoAssignAudit.get("action").asText()).isEqualTo("AUTO_ASSIGN");
        JsonNode ticketAudit = expectOk(getJson(
                "/audit-logs?entityType=TICKET&entityId=" + autoAssignedTicket.get("id").asLong() + "&size=20",
                adminToken
        ));
        assertThat(ticketAudit).isNotEmpty();

        // 17. Validation and error handling.
        assertThat(postJson("/tickets", json(
                "projectId", projectId,
                "title", "Bad enum",
                "status", "INVALID"
        ), adminToken).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(postJson("/tickets", json(
                "projectId", projectId
        ), adminToken).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        HttpHeaders malformedHeaders = jsonHeaders(adminToken);
        assertThat(rest.exchange(
                "/tickets",
                HttpMethod.POST,
                new HttpEntity<>("{not-json", malformedHeaders),
                JsonNode.class
        ).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(getJson("/tickets/999999", adminToken).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    private JsonNode seedUser(String username, String email, String fullName, String role) {
        User user = new User();
        user.setUsername(username);
        user.setEmail(email);
        user.setFullName(fullName);
        user.setRole(com.att.tdp.issueflow.user.Role.valueOf(role));
        User savedUser = userRepository.saveAndFlush(user);
        return jsonNode(json(
                "id", savedUser.getId(),
                "username", savedUser.getUsername(),
                "email", savedUser.getEmail(),
                "fullName", savedUser.getFullName(),
                "role", savedUser.getRole().name()
        ));
    }

    private JsonNode createUser(String username, String email, String fullName, String role, String token) {
        return expectCreated(postJson("/users", json(
                "username", username,
                "email", email,
                "fullName", fullName,
                "role", role
        ), token));
    }

    private String login(String username, String password) {
        JsonNode body = expectOk(postJson("/auth/login", json("username", username, "password", password), null));
        return body.get("accessToken").asText();
    }

    private JsonNode createTicket(String token, Map<String, Object> request) {
        Map<String, Object> completeRequest = new LinkedHashMap<>(request);
        completeRequest.putIfAbsent("status", "TODO");
        completeRequest.putIfAbsent("type", "FEATURE");
        completeRequest.putIfAbsent("priority", "MEDIUM");
        return expectCreated(postJson("/tickets", completeRequest, token));
    }

    private JsonNode updateTicket(String token, JsonNode currentTicket, Map<String, Object> patch) {
        JsonNode latestTicket = expectOk(getJson("/tickets/" + currentTicket.get("id").asLong(), token));
        Map<String, Object> body = new LinkedHashMap<>(patch);
        assertThat(patchJson("/tickets/" + latestTicket.get("id").asLong(), body, token).getStatusCode())
                .isEqualTo(HttpStatus.OK);
        return expectOk(getJson("/tickets/" + latestTicket.get("id").asLong(), token));
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

    private ResponseEntity<JsonNode> getJson(String path, String token) {
        return rest.exchange(path, HttpMethod.GET, new HttpEntity<>(authHeaders(token)), JsonNode.class);
    }

    private ResponseEntity<JsonNode> postJson(String path, Object body, String token) {
        return rest.exchange(path, HttpMethod.POST, new HttpEntity<>(body, jsonHeaders(token)), JsonNode.class);
    }

    private ResponseEntity<JsonNode> patchJson(String path, Object body, String token) {
        return rest.exchange(path, HttpMethod.PATCH, new HttpEntity<>(body, jsonHeaders(token)), JsonNode.class);
    }

    private ResponseEntity<Void> delete(String path, String token) {
        return rest.exchange(path, HttpMethod.DELETE, new HttpEntity<>(authHeaders(token)), Void.class);
    }

    private ResponseEntity<JsonNode> upload(
            String path,
            String filename,
            String contentType,
            byte[] bytes,
            String token
    ) {
        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add("file", filePart(filename, contentType, bytes));
        return rest.exchange(path, HttpMethod.POST, new HttpEntity<>(body, multipartHeaders(token)), JsonNode.class);
    }

    private ResponseEntity<JsonNode> importCsv(long projectId, String csv, String token) {
        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add("projectId", String.valueOf(projectId));
        body.add("file", filePart("tickets.csv", "text/csv", csv.getBytes(StandardCharsets.UTF_8)));
        return rest.exchange(
                "/tickets/import?projectId=" + projectId,
                HttpMethod.POST,
                new HttpEntity<>(body, multipartHeaders(token)),
                JsonNode.class
        );
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

    private JsonNode expectCreated(ResponseEntity<JsonNode> response) {
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        return response.getBody();
    }

    private JsonNode expectOk(ResponseEntity<JsonNode> response) {
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        return response.getBody();
    }

    private Map<String, Object> json(Object... keyValues) {
        Map<String, Object> body = new LinkedHashMap<>();
        for (int index = 0; index < keyValues.length; index += 2) {
            body.put((String) keyValues[index], keyValues[index + 1]);
        }
        return body;
    }

    private JsonNode jsonNode(Map<String, Object> body) {
        return rest.getRestTemplate().getMessageConverters().stream()
                .filter(converter -> converter instanceof org.springframework.http.converter.json.MappingJackson2HttpMessageConverter)
                .map(converter -> (org.springframework.http.converter.json.MappingJackson2HttpMessageConverter) converter)
                .findFirst()
                .orElseThrow()
                .getObjectMapper()
                .valueToTree(body);
    }

    private List<Long> ids(JsonNode array) {
        List<Long> ids = new ArrayList<>();
        array.forEach(node -> ids.add(node.get("id").asLong()));
        return ids;
    }

    private JsonNode findById(JsonNode array, long id) {
        for (JsonNode node : array) {
            if (node.get("id").asLong() == id) {
                return node;
            }
        }
        throw new AssertionError("Expected response array to contain id " + id);
    }

    private List<String> actions(JsonNode array) {
        List<String> actions = new ArrayList<>();
        array.forEach(node -> actions.add(node.get("action").asText()));
        return actions;
    }

    private long openTicketCount(JsonNode workload, long userId) {
        for (JsonNode member : workload) {
            if (member.get("userId").asLong() == userId) {
                return member.get("openTicketCount").asLong();
            }
        }
        throw new AssertionError("Missing workload member " + userId);
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
