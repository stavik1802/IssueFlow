package com.att.tdp.issueflow;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.att.tdp.issueflow.audit.AuditAction;
import com.att.tdp.issueflow.audit.AuditActorType;
import com.att.tdp.issueflow.audit.AuditLogRepository;
import com.att.tdp.issueflow.audit.AuditableEntityType;
import com.att.tdp.issueflow.attachment.AttachmentRepository;
import com.att.tdp.issueflow.project.Project;
import com.att.tdp.issueflow.project.ProjectMember;
import com.att.tdp.issueflow.project.ProjectMemberRepository;
import com.att.tdp.issueflow.project.ProjectRepository;
import com.att.tdp.issueflow.security.jwt.JwtTokenService;
import com.att.tdp.issueflow.ticket.Ticket;
import com.att.tdp.issueflow.ticket.TicketPriority;
import com.att.tdp.issueflow.ticket.TicketRepository;
import com.att.tdp.issueflow.ticket.TicketStatus;
import com.att.tdp.issueflow.ticket.TicketType;
import com.att.tdp.issueflow.user.Role;
import com.att.tdp.issueflow.user.User;
import com.att.tdp.issueflow.user.UserRepository;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "spring.jpa.database=H2",
        "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect"
})
class RequirementContractIT {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtTokenService jwtTokenService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ProjectRepository projectRepository;

    @Autowired
    private ProjectMemberRepository projectMemberRepository;

    @Autowired
    private TicketRepository ticketRepository;

    @Autowired
    private AttachmentRepository attachmentRepository;

    @Autowired
    private AuditLogRepository auditLogRepository;

    @Test
    void userCreateUsesReadmeCamelCaseFullNameAndOkStatus() throws Exception {
        String suffix = suffix();
        String adminToken = bearer(seedUser("admin-" + suffix, Role.ADMIN));

        mockMvc.perform(post("/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "username": "unauth-%s",
                                  "email": "unauth-%s@example.com",
                                  "fullName": "Unauthenticated User",
                                  "role": "DEVELOPER"
                                }
                                """.formatted(suffix, suffix)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("unauth-" + suffix))
                .andExpect(jsonPath("$.email").value("unauth-" + suffix + "@example.com"))
                .andExpect(jsonPath("$.fullName").value("Unauthenticated User"))
                .andExpect(jsonPath("$.role").value("DEVELOPER"))
                .andExpect(jsonPath("$.createdAt").doesNotExist())
                .andExpect(jsonPath("$.updatedAt").doesNotExist());

        mockMvc.perform(post("/users")
                        .header("Authorization", adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "username": "auth-%s",
                                  "email": "auth-%s@example.com",
                                  "fullName": "Authenticated User",
                                  "role": "DEVELOPER"
                                }
                                """.formatted(suffix, suffix)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fullName").value("Authenticated User"))
                .andExpect(jsonPath("$.createdAt").doesNotExist())
                .andExpect(jsonPath("$.updatedAt").doesNotExist());

        mockMvc.perform(post("/users")
                        .header("Authorization", adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "username": "registry-%s",
                                  "email": "registry-%s@example.com",
                                  "fullName": "Registry User",
                                  "role": "DEVELOPER"
                                }
                                """.formatted(suffix, suffix)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fullName").value("Registry User"))
                .andExpect(jsonPath("$.full_name").doesNotExist());
    }

    @Test
    void projectCreateRequiresDescriptionAndPatchAllowsDescriptionOnly() throws Exception {
        String suffix = suffix();
        User owner = seedUser("owner-" + suffix, Role.ADMIN);
        String token = bearer(owner);

        mockMvc.perform(post("/projects")
                        .header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "Missing Description",
                                  "ownerId": %d
                                }
                                """.formatted(owner.getId())))
                .andExpect(status().isBadRequest());

        MvcResult created = mockMvc.perform(post("/projects")
                        .header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "Contract Project",
                                  "description": "Original description",
                                  "ownerId": %d
                                }
                                """.formatted(owner.getId())))
                .andExpect(status().isOk())
                .andReturn();
        String projectId = com.jayway.jsonpath.JsonPath.read(created.getResponse().getContentAsString(), "$.id").toString();

        mockMvc.perform(patch("/projects/" + projectId)
                        .header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "description": "Description only"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(content().string(""));

        mockMvc.perform(get("/projects/" + projectId)
                        .header("Authorization", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Contract Project"))
                .andExpect(jsonPath("$.description").value("Description only"))
                .andExpect(jsonPath("$.key").doesNotExist())
                .andExpect(jsonPath("$.memberCount").doesNotExist())
                .andExpect(jsonPath("$.createdAt").doesNotExist())
                .andExpect(jsonPath("$.updatedAt").doesNotExist())
                .andExpect(jsonPath("$.deletedAt").doesNotExist());
    }

    @Test
    void ticketCreateRequiresDeclaredFieldsDerivesReporterAndAllowsExistingAssignee() throws Exception {
        String suffix = suffix();
        User reporter = seedUser("reporter-" + suffix, Role.DEVELOPER);
        User assignee = seedUser("assignee-" + suffix, Role.DEVELOPER);
        Project project = seedProject("Tickets " + suffix, reporter);
        String token = bearer(reporter);

        mockMvc.perform(post("/tickets")
                        .header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "projectId": %d,
                                  "title": "Missing required ticket fields"
                                }
                                """.formatted(project.getId())))
                .andExpect(status().isBadRequest());

        mockMvc.perform(post("/tickets")
                        .header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "projectId": %d,
                                  "assigneeId": %d,
                                  "title": "Contract ticket",
                                  "description": "Complete ticket",
                                  "status": "TODO",
                                  "priority": "HIGH",
                                  "type": "BUG"
                                }
                                """.formatted(project.getId(), assignee.getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reporterId").doesNotExist())
                .andExpect(jsonPath("$.assigneeId").value(assignee.getId()))
                .andExpect(jsonPath("$.status").value("TODO"))
                .andExpect(jsonPath("$.priority").value("HIGH"))
                .andExpect(jsonPath("$.type").value("BUG"))
                .andExpect(jsonPath("$.resolvedAt").doesNotExist())
                .andExpect(jsonPath("$.deletedAt").doesNotExist())
                .andExpect(jsonPath("$.version").doesNotExist());
    }

    @Test
    void dependencyDeleteRequiresExistingDependencyAndDoesNotCreateFalseAudit() throws Exception {
        String suffix = suffix();
        User user = seedUser("dep-user-" + suffix, Role.DEVELOPER);
        Project project = seedProject("Dependency " + suffix, user);
        Ticket ticket = seedTicket(project, user, "Blocked");
        Ticket blocker = seedTicket(project, user, "Blocker");
        long auditCountBefore = removeDependencyAuditCount(ticket.getId());

        mockMvc.perform(delete("/tickets/" + ticket.getId() + "/dependencies/" + blocker.getId())
                        .header("Authorization", bearer(user)))
                .andExpect(status().isNotFound());

        org.assertj.core.api.Assertions.assertThat(removeDependencyAuditCount(ticket.getId()))
                .isEqualTo(auditCountBefore);
    }

    @Test
    void ticketImportReportsMissingRequiredFieldsAndStillCreatesValidRows() throws Exception {
        String suffix = suffix();
        User user = seedUser("import-user-" + suffix, Role.DEVELOPER);
        Project project = seedProject("Import " + suffix, user);
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "tickets.csv",
                "text/csv",
                """
                        id,title,description,status,priority,type,assigneeId
                        ,Missing description,,TODO,HIGH,BUG,
                        ,Missing status,Plain body,,HIGH,BUG,
                        ,Valid,Plain body,TODO,HIGH,BUG,
                        """.getBytes()
        );

        mockMvc.perform(multipart("/tickets/import")
                        .file(file)
                        .param("projectId", project.getId().toString())
                        .header("Authorization", bearer(user)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.created").value(1))
                .andExpect(jsonPath("$.failed").value(2))
                .andExpect(jsonPath("$.errors[0].message").value("description is required"))
                .andExpect(jsonPath("$.errors[1].message").value("status is required"));
    }

    @Test
    void ticketImportRejectsNonCsvFiles() throws Exception {
        String suffix = suffix();
        User user = seedUser("import-type-user-" + suffix, Role.DEVELOPER);
        Project project = seedProject("Import Type " + suffix, user);
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "tickets.json",
                "application/json",
                "{}".getBytes()
        );

        mockMvc.perform(multipart("/tickets/import")
                        .file(file)
                        .param("projectId", project.getId().toString())
                        .header("Authorization", bearer(user)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("CSV file type is required"));
    }

    @Test
    void softDeletedProjectHidesTicketFromStandardTicketApis() throws Exception {
        String suffix = suffix();
        User admin = seedUser("soft-admin-" + suffix, Role.ADMIN);
        Project project = seedProject("Soft Delete " + suffix, admin);
        Ticket ticket = seedTicket(project, admin, "Hidden ticket");
        String token = bearer(admin);

        mockMvc.perform(delete("/projects/" + project.getId())
                        .header("Authorization", token))
                .andExpect(status().isOk());

        mockMvc.perform(get("/tickets/" + ticket.getId())
                        .header("Authorization", token))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/tickets")
                        .param("projectId", project.getId().toString())
                        .header("Authorization", token))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/tickets/export")
                        .param("projectId", project.getId().toString())
                        .header("Authorization", token))
                .andExpect(status().isNotFound());
    }

    @Test
    void projectDeleteSoftDeletesAndRestoresChildTicketsAndCommentsAsSystem() throws Exception {
        String suffix = suffix();
        User admin = seedUser("cascade-admin-" + suffix, Role.ADMIN);
        Project project = seedProject("Cascade Delete " + suffix, admin);
        Ticket ticket = seedTicket(project, admin, "Cascade ticket");
        String token = bearer(admin);

        mockMvc.perform(post("/tickets/" + ticket.getId() + "/comments")
                        .header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "authorId": %d,
                                  "content": "Cascade comment @%s"
                                }
                                """.formatted(admin.getId(), admin.getUsername())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mentionedUsers[0].id").value(admin.getId()));

        mockMvc.perform(delete("/projects/" + project.getId())
                        .header("Authorization", token))
                .andExpect(status().isOk());

        mockMvc.perform(get("/tickets/" + ticket.getId())
                        .header("Authorization", token))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/users/" + admin.getId() + "/mentions")
                        .header("Authorization", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isEmpty());

        assertThat(systemAuditCount(AuditAction.DELETE, AuditableEntityType.TICKET, ticket.getId())).isEqualTo(1);
        assertThat(systemAuditCount(AuditAction.DELETE, AuditableEntityType.COMMENT, null)).isGreaterThanOrEqualTo(1);
        assertThat(systemAuditPerformedBy(AuditAction.DELETE, AuditableEntityType.TICKET, ticket.getId()))
                .contains(admin.getId());

        mockMvc.perform(post("/projects/" + project.getId() + "/restore")
                        .header("Authorization", token))
                .andExpect(status().isOk());

        mockMvc.perform(get("/tickets/" + ticket.getId())
                        .header("Authorization", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(ticket.getId()));
        mockMvc.perform(get("/tickets/" + ticket.getId() + "/comments")
                        .header("Authorization", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].content").value("Cascade comment @" + admin.getUsername()));

        assertThat(systemAuditCount(AuditAction.RESTORE, AuditableEntityType.TICKET, ticket.getId())).isEqualTo(1);
        assertThat(systemAuditCount(AuditAction.RESTORE, AuditableEntityType.COMMENT, null)).isGreaterThanOrEqualTo(1);
        assertThat(systemAuditPerformedBy(AuditAction.RESTORE, AuditableEntityType.TICKET, ticket.getId()))
                .contains(admin.getId());
    }

    @Test
    void restoredTicketDoesNotReassignToRecreatedUserWithSameInputs() throws Exception {
        String suffix = suffix();
        User admin = seedUser("restore-admin-" + suffix, Role.ADMIN);
        User originalAssignee = seedUser("restore-dev-" + suffix, Role.DEVELOPER);
        Project project = seedProject("Restore Identity " + suffix, admin);
        Ticket ticket = seedTicket(project, admin, "Restore identity ticket");
        ticket.setAssignee(originalAssignee);
        ticketRepository.saveAndFlush(ticket);
        String token = bearer(admin);

        mockMvc.perform(delete("/tickets/" + ticket.getId())
                        .header("Authorization", token))
                .andExpect(status().isOk());
        mockMvc.perform(delete("/users/" + originalAssignee.getId())
                        .header("Authorization", token))
                .andExpect(status().isOk());

        mockMvc.perform(post("/users")
                        .header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "username": "%s",
                                  "email": "%s",
                                  "fullName": "%s",
                                  "role": "DEVELOPER"
                                }
                                """.formatted(
                                        originalAssignee.getUsername(),
                                        originalAssignee.getEmail(),
                                        originalAssignee.getFullName()
                                )))
                .andExpect(status().isOk());

        User recreated = userRepository.findByUsername(originalAssignee.getUsername()).orElseThrow();
        assertThat(recreated.getId()).isNotEqualTo(originalAssignee.getId());

        mockMvc.perform(post("/tickets/" + ticket.getId() + "/restore")
                        .header("Authorization", token))
                .andExpect(status().isOk());
        mockMvc.perform(get("/tickets/" + ticket.getId())
                        .header("Authorization", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.assigneeId").doesNotExist());
    }

    @Test
    void ticketRestoreRestoresCommentsMentionsAndAttachments() throws Exception {
        String suffix = suffix();
        User admin = seedUser("restore-assets-admin-" + suffix, Role.ADMIN);
        User mentioned = seedUser("restore-assets-mentioned-" + suffix, Role.DEVELOPER);
        Project project = seedProject("Restore Assets " + suffix, admin);
        Ticket ticket = seedTicket(project, admin, "Restore assets ticket");
        String token = bearer(admin);

        mockMvc.perform(post("/tickets/" + ticket.getId() + "/comments")
                        .header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "authorId": %d,
                                  "content": "Please check this @%s"
                                }
                                """.formatted(admin.getId(), mentioned.getUsername())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mentionedUsers[0].id").value(mentioned.getId()));

        MockMultipartFile file = new MockMultipartFile(
                "file",
                "restore-note.txt",
                "text/plain",
                "restore me".getBytes()
        );
        mockMvc.perform(multipart("/tickets/" + ticket.getId() + "/attachments")
                        .file(file)
                        .header("Authorization", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.filename").value("restore-note.txt"));

        mockMvc.perform(delete("/tickets/" + ticket.getId())
                        .header("Authorization", token))
                .andExpect(status().isOk());

        mockMvc.perform(get("/tickets/" + ticket.getId() + "/comments")
                        .header("Authorization", token))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/users/" + mentioned.getId() + "/mentions")
                        .header("Authorization", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isEmpty());
        assertThat(attachmentRepository.findActiveByTicketIdIncludingDeletedTicket(ticket.getId())).isEmpty();

        mockMvc.perform(post("/tickets/" + ticket.getId() + "/restore")
                        .header("Authorization", token))
                .andExpect(status().isOk());

        mockMvc.perform(get("/tickets/" + ticket.getId() + "/comments")
                        .header("Authorization", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].content").value("Please check this @" + mentioned.getUsername()))
                .andExpect(jsonPath("$[0].mentionedUsers[0].id").value(mentioned.getId()))
                .andExpect(jsonPath("$[0].mentionedUsers[0].username").value(mentioned.getUsername()));
        mockMvc.perform(get("/users/" + mentioned.getId() + "/mentions")
                        .header("Authorization", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].mentionedUsers[0].id").value(mentioned.getId()));
        assertThat(attachmentRepository.findActiveByTicketIdIncludingDeletedTicket(ticket.getId()))
                .singleElement()
                .satisfies(attachment -> {
                    assertThat(attachment.getFileName()).isEqualTo("restore-note.txt");
                    assertThat(attachment.getContentType()).isEqualTo("text/plain");
                });
    }

    @Test
    void ticketRestoreRestoresDependenciesAndDeletedBlockerStopsBlockingStatusChanges() throws Exception {
        String suffix = suffix();
        User admin = seedUser("restore-deps-admin-" + suffix, Role.ADMIN);
        Project project = seedProject("Restore Dependencies " + suffix, admin);
        Ticket blocker = seedTicket(project, admin, "Blocking ticket");
        Ticket blocked = seedTicket(project, admin, "Blocked ticket");
        String token = bearer(admin);

        mockMvc.perform(post("/tickets/" + blocked.getId() + "/dependencies")
                        .header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "blockedBy": %d
                                }
                                """.formatted(blocker.getId())))
                .andExpect(status().isOk())
                .andExpect(content().string(""));

        mockMvc.perform(get("/tickets/" + blocked.getId() + "/dependencies")
                        .header("Authorization", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(blocker.getId()))
                .andExpect(jsonPath("$[0].title").value("Blocking ticket"))
                .andExpect(jsonPath("$[0].status").value("TODO"))
                .andExpect(jsonPath("$[0].ticketId").doesNotExist())
                .andExpect(jsonPath("$[0].blockedBy").doesNotExist());

        mockMvc.perform(delete("/tickets/" + blocked.getId())
                        .header("Authorization", token))
                .andExpect(status().isOk());

        mockMvc.perform(post("/tickets/" + blocked.getId() + "/restore")
                        .header("Authorization", token))
                .andExpect(status().isOk());

        mockMvc.perform(get("/tickets/" + blocked.getId() + "/dependencies")
                        .header("Authorization", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(blocker.getId()));

        mockMvc.perform(delete("/tickets/" + blocker.getId())
                        .header("Authorization", token))
                .andExpect(status().isOk());

        mockMvc.perform(get("/tickets/" + blocked.getId() + "/dependencies")
                        .header("Authorization", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isEmpty());

        mockMvc.perform(patch("/tickets/" + blocked.getId())
                        .header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "status": "IN_PROGRESS"
                                }
                                """))
                .andExpect(status().isOk());
        mockMvc.perform(patch("/tickets/" + blocked.getId())
                        .header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "status": "IN_REVIEW"
                                }
                                """))
                .andExpect(status().isOk());
        mockMvc.perform(patch("/tickets/" + blocked.getId())
                        .header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "status": "DONE"
                                }
                                """))
                .andExpect(status().isOk());

        mockMvc.perform(post("/tickets/" + blocker.getId() + "/restore")
                        .header("Authorization", token))
                .andExpect(status().isOk());

        mockMvc.perform(get("/tickets/" + blocked.getId() + "/dependencies")
                        .header("Authorization", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(blocker.getId()));

        assertThat(systemAuditCount(AuditAction.DELETE, AuditableEntityType.TICKET_DEPENDENCY, null))
                .isGreaterThanOrEqualTo(2);
        assertThat(systemAuditCount(AuditAction.RESTORE, AuditableEntityType.TICKET_DEPENDENCY, null))
                .isGreaterThanOrEqualTo(2);
    }

    @Test
    void ticketResponseIncludesRequiredIsOverdueJsonField() throws Exception {
        String suffix = suffix();
        User reporter = seedUser("overdue-reporter-" + suffix, Role.DEVELOPER);
        Project project = seedProject("Overdue " + suffix, reporter);

        mockMvc.perform(post("/tickets")
                        .header("Authorization", bearer(reporter))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "projectId": %d,
                                  "title": "Overdue contract ticket",
                                  "description": "Complete ticket",
                                  "status": "TODO",
                                  "priority": "HIGH",
                                  "type": "BUG"
                                }
                                """.formatted(project.getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.isOverdue").value(false))
                .andExpect(jsonPath("$.is_overdue").doesNotExist());
    }

    @Test
    void workloadIncludesAdminsButAutoAssignmentChoosesDeveloperOnly() throws Exception {
        String suffix = suffix();
        User admin = seedUser("workload-admin-" + suffix, Role.ADMIN);
        User developer = seedUser("workload-dev-" + suffix, Role.DEVELOPER);
        Project project = seedProject("Workload " + suffix, admin);
        seedProjectMember(project, admin);
        seedProjectMember(project, developer);

        mockMvc.perform(get("/projects/" + project.getId() + "/workload")
                        .header("Authorization", bearer(admin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].userId").value(admin.getId()))
                .andExpect(jsonPath("$[1].userId").value(developer.getId()));

        mockMvc.perform(post("/tickets")
                        .header("Authorization", bearer(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "projectId": %d,
                                  "title": "Auto assigned",
                                  "description": "Complete ticket",
                                  "status": "TODO",
                                  "priority": "MEDIUM",
                                  "type": "FEATURE"
                                }
                                """.formatted(project.getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.assigneeId").value(developer.getId()));
    }

    @Test
    void frameworkInputErrorsReturnConsistentApiErrors() throws Exception {
        String suffix = suffix();
        User user = seedUser("errors-" + suffix, Role.ADMIN);
        String token = bearer(user);

        mockMvc.perform(get("/tickets/not-a-number")
                        .header("Authorization", token))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Invalid value for parameter: id"));

        mockMvc.perform(get("/tickets/export")
                        .header("Authorization", token))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Missing required parameter: projectId"));

        mockMvc.perform(multipart("/tickets/1/attachments")
                        .header("Authorization", token))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Missing required multipart part: file"));
    }

    @Test
    void oversizedMultipartUploadReturnsInformativeClientError() throws Exception {
        String suffix = suffix();
        User user = seedUser("big-upload-" + suffix, Role.DEVELOPER);
        Project project = seedProject("Upload " + suffix, user);
        Ticket ticket = seedTicket(project, user, "Upload ticket");
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "big.txt",
                "text/plain",
                new byte[10 * 1024 * 1024 + 1]
        );

        mockMvc.perform(multipart("/tickets/" + ticket.getId() + "/attachments")
                        .file(file)
                        .header("Authorization", bearer(user)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Attachment exceeds the 10 MB size limit"));
    }

    @Test
    void duplicateUserConstraintReturnsConflictApiError() throws Exception {
        String suffix = suffix();
        User admin = seedUser("dupe-admin-" + suffix, Role.ADMIN);
        String token = bearer(admin);

        mockMvc.perform(post("/users")
                        .header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "username": "dupe-%s",
                                  "email": "dupe-%s@example.com",
                                  "fullName": "Duplicate User",
                                  "role": "DEVELOPER"
                                }
                                """.formatted(suffix, suffix)))
                .andExpect(status().isOk());

        mockMvc.perform(post("/users")
                        .header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "username": "dupe-%s",
                                  "email": "different-%s@example.com",
                                  "fullName": "Duplicate User",
                                  "role": "DEVELOPER"
                                }
                                """.formatted(suffix, suffix)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Username already exists"));
    }

    @Test
    void exactDuplicateCreatesReturnClearConflictErrors() throws Exception {
        String suffix = suffix();
        User admin = seedUser("exact-dupe-admin-" + suffix, Role.ADMIN);
        User assignee = seedUser("exact-dupe-assignee-" + suffix, Role.DEVELOPER);
        String token = bearer(admin);

        mockMvc.perform(post("/users")
                        .header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "username": "exact-dupe-user-%s",
                                  "email": "exact-dupe-user-%s@example.com",
                                  "fullName": "Exact Duplicate User",
                                  "role": "DEVELOPER"
                                }
                                """.formatted(suffix, suffix)))
                .andExpect(status().isOk());
        mockMvc.perform(post("/users")
                        .header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "username": "exact-dupe-user-%s",
                                  "email": "exact-dupe-user-%s@example.com",
                                  "fullName": "Exact Duplicate User",
                                  "role": "DEVELOPER"
                                }
                                """.formatted(suffix, suffix)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Duplicate user"));

        mockMvc.perform(post("/projects")
                        .header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "Exact Duplicate Project %s",
                                  "description": "Same project fields",
                                  "ownerId": %d
                                }
                                """.formatted(suffix, admin.getId())))
                .andExpect(status().isOk());
        mockMvc.perform(post("/projects")
                        .header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "Exact Duplicate Project %s",
                                  "description": "Same project fields",
                                  "ownerId": %d
                                }
                                """.formatted(suffix, admin.getId())))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Duplicate project"));

        Project project = seedProject("Exact Duplicate Ticket Project " + suffix, admin);
        String duplicateTicketJson = """
                {
                  "projectId": %d,
                  "assigneeId": %d,
                  "title": "Exact duplicate ticket",
                  "description": "Same ticket fields",
                  "status": "TODO",
                  "priority": "HIGH",
                  "type": "BUG"
                }
                """.formatted(project.getId(), assignee.getId());
        mockMvc.perform(post("/tickets")
                        .header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(duplicateTicketJson))
                .andExpect(status().isOk());
        mockMvc.perform(post("/tickets")
                        .header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(duplicateTicketJson))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Duplicate ticket"));

        Ticket ticket = seedTicket(project, admin, "Exact duplicate comment ticket");
        String duplicateCommentJson = """
                {
                  "authorId": %d,
                  "content": "Same comment body"
                }
                """.formatted(admin.getId());
        mockMvc.perform(post("/tickets/" + ticket.getId() + "/comments")
                        .header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(duplicateCommentJson))
                .andExpect(status().isOk());
        mockMvc.perform(post("/tickets/" + ticket.getId() + "/comments")
                        .header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(duplicateCommentJson))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Duplicate comment"));
    }

    @Test
    void ticketImportReportsDuplicateRowsAsFailures() throws Exception {
        String suffix = suffix();
        User user = seedUser("import-dupe-user-" + suffix, Role.DEVELOPER);
        Project project = seedProject("Import Duplicate " + suffix, user);
        seedTicket(project, user, "Imported duplicate ticket");
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "tickets.csv",
                "text/csv",
                """
                        id,title,description,status,priority,type,assigneeId
                        ,Imported duplicate ticket,Seed ticket,TODO,MEDIUM,FEATURE,
                        """.getBytes()
        );

        mockMvc.perform(multipart("/tickets/import")
                        .file(file)
                        .param("projectId", project.getId().toString())
                        .header("Authorization", bearer(user)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.created").value(0))
                .andExpect(jsonPath("$.failed").value(1))
                .andExpect(jsonPath("$.errors[0].message").value("Duplicate ticket"));
    }

    @Test
    void readmeUserUpdateRouteUsesPostUsersUpdateAndReturnsNoBody() throws Exception {
        String suffix = suffix();
        User admin = seedUser("update-admin-" + suffix, Role.ADMIN);
        User user = seedUser("update-user-" + suffix, Role.DEVELOPER);

        mockMvc.perform(post("/users/update/" + user.getId())
                        .header("Authorization", bearer(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "fullName": "Updated From Readme Route",
                                  "role": "ADMIN"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(content().string(""));

        mockMvc.perform(get("/users/" + user.getId())
                        .header("Authorization", bearer(admin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fullName").value("Updated From Readme Route"))
                .andExpect(jsonPath("$.role").value("ADMIN"))
                .andExpect(jsonPath("$.createdAt").doesNotExist())
                .andExpect(jsonPath("$.updatedAt").doesNotExist());
    }

    @Test
    void auditLogsReturnReadmeArrayShapeAndSupportActorFilter() throws Exception {
        String suffix = suffix();
        User admin = seedUser("audit-admin-" + suffix, Role.ADMIN);
        User user = seedUser("audit-user-" + suffix, Role.DEVELOPER);

        mockMvc.perform(post("/users/update/" + user.getId())
                        .header("Authorization", bearer(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "fullName": "Audit Updated",
                                  "role": "DEVELOPER"
                                }
                                """))
                .andExpect(status().isOk());

        mockMvc.perform(get("/audit-logs")
                        .param("actor", "USER")
                        .param("action", "UPDATE")
                        .param("entityType", "USER")
                        .param("entityId", user.getId().toString())
                        .header("Authorization", bearer(admin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].action").value("UPDATE"))
                .andExpect(jsonPath("$[0].entityType").value("USER"))
                .andExpect(jsonPath("$[0].entityId").value(user.getId()))
                .andExpect(jsonPath("$[0].performedBy").value(admin.getId()))
                .andExpect(jsonPath("$[0].actor").value("USER"))
                .andExpect(jsonPath("$[0].timestamp").exists())
                .andExpect(jsonPath("$[0].actorType").doesNotExist())
                .andExpect(jsonPath("$[0].actorId").doesNotExist())
                .andExpect(jsonPath("$[0].oldValue").doesNotExist())
                .andExpect(jsonPath("$[0].newValue").doesNotExist())
                .andExpect(jsonPath("$[0].createdAt").doesNotExist());
    }

    @Test
    void auditLogsUseAuthenticatedActorForCreateAuthCommentAndExport() throws Exception {
        String suffix = suffix();
        User admin = seedUser("audit-actor-admin-" + suffix, Role.ADMIN);
        User auditor = seedUser("audit-actor-reader-" + suffix, Role.ADMIN);
        User owner = seedUser("audit-actor-owner-" + suffix, Role.DEVELOPER);
        String adminToken = bearer(admin);
        String auditorToken = bearer(auditor);

        MvcResult login = mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "username": "%s",
                                  "password": "secret"
                                }
                                """.formatted(admin.getUsername())))
                .andExpect(status().isOk())
                .andReturn();
        String loginToken = "Bearer " + com.jayway.jsonpath.JsonPath.read(
                login.getResponse().getContentAsString(),
                "$.accessToken"
        );

        mockMvc.perform(get("/audit-logs")
                        .param("action", "LOGIN")
                        .param("entityType", "AUTH")
                        .param("entityId", admin.getId().toString())
                        .header("Authorization", adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].performedBy").value(admin.getId()))
                .andExpect(jsonPath("$[0].actor").value("USER"));

        MvcResult createdUser = mockMvc.perform(post("/users")
                        .header("Authorization", adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "username": "audit-created-user-%s",
                                  "email": "audit-created-user-%s@example.com",
                                  "fullName": "Audit Created User",
                                  "role": "DEVELOPER"
                                }
                                """.formatted(suffix, suffix)))
                .andExpect(status().isOk())
                .andReturn();
        String createdUserId = com.jayway.jsonpath.JsonPath.read(
                createdUser.getResponse().getContentAsString(),
                "$.id"
        ).toString();

        mockMvc.perform(get("/audit-logs")
                        .param("action", "CREATE")
                        .param("entityType", "USER")
                        .param("entityId", createdUserId)
                        .header("Authorization", adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].performedBy").value(admin.getId()));

        MvcResult createdProject = mockMvc.perform(post("/projects")
                        .header("Authorization", adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "Audit Actor Project %s",
                                  "description": "Created for audit actor test",
                                  "ownerId": %d
                                }
                                """.formatted(suffix, owner.getId())))
                .andExpect(status().isOk())
                .andReturn();
        String projectId = com.jayway.jsonpath.JsonPath.read(
                createdProject.getResponse().getContentAsString(),
                "$.id"
        ).toString();

        mockMvc.perform(get("/audit-logs")
                        .param("action", "CREATE")
                        .param("entityType", "PROJECT")
                        .param("entityId", projectId)
                        .header("Authorization", adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].performedBy").value(admin.getId()));

        Ticket ticket = seedTicket(
                projectRepository.findById(Long.valueOf(projectId)).orElseThrow(),
                admin,
                "Audit actor comment ticket"
        );
        MvcResult comment = mockMvc.perform(post("/tickets/" + ticket.getId() + "/comments")
                        .header("Authorization", adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "authorId": %d,
                                  "content": "Audit actor comment"
                                }
                                """.formatted(owner.getId())))
                .andExpect(status().isOk())
                .andReturn();
        String commentId = com.jayway.jsonpath.JsonPath.read(
                comment.getResponse().getContentAsString(),
                "$.id"
        ).toString();

        mockMvc.perform(get("/audit-logs")
                        .param("action", "CREATE")
                        .param("entityType", "COMMENT")
                        .param("entityId", commentId)
                        .header("Authorization", adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].performedBy").value(admin.getId()));

        mockMvc.perform(get("/tickets/export")
                        .param("projectId", projectId)
                        .header("Authorization", adminToken))
                .andExpect(status().isOk());
        mockMvc.perform(get("/audit-logs")
                        .param("action", "EXPORT_TICKETS")
                        .param("entityType", "PROJECT")
                        .param("entityId", projectId)
                        .header("Authorization", adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].performedBy").value(admin.getId()))
                .andExpect(jsonPath("$[0].entityType").value("PROJECT"));

        mockMvc.perform(post("/auth/logout")
                        .header("Authorization", loginToken))
                .andExpect(status().isOk())
                .andExpect(content().string(""));
        mockMvc.perform(get("/audit-logs")
                        .param("action", "LOGOUT")
                        .param("entityType", "AUTH")
                        .param("entityId", admin.getId().toString())
                        .header("Authorization", auditorToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].performedBy").value(admin.getId()));
    }

    @Test
    void mentionsReturnReadmePaginationShapeAndCamelCaseMentionedUsers() throws Exception {
        String suffix = suffix();
        User author = seedUser("mention-author-" + suffix, Role.DEVELOPER);
        User mentioned = seedUser("mention-user-" + suffix, Role.DEVELOPER);
        Project project = seedProject("Mentions " + suffix, author);
        Ticket ticket = seedTicket(project, author, "Mentioned ticket");

        mockMvc.perform(post("/tickets/" + ticket.getId() + "/comments")
                        .header("Authorization", bearer(author))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "authorId": %d,
                                  "content": "Hello @%s"
                                }
                                """.formatted(author.getId(), mentioned.getUsername().toUpperCase(Locale.ROOT))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mentionedUsers[0].id").value(mentioned.getId()))
                .andExpect(jsonPath("$.mentionedUsers[0].fullName").value(mentioned.getFullName()))
                .andExpect(jsonPath("$.authorUsername").doesNotExist())
                .andExpect(jsonPath("$.createdAt").doesNotExist())
                .andExpect(jsonPath("$.updatedAt").doesNotExist())
                .andExpect(jsonPath("$.editedAt").doesNotExist())
                .andExpect(jsonPath("$.version").doesNotExist());

        mockMvc.perform(get("/users/" + mentioned.getId() + "/mentions")
                        .param("page", "1")
                        .param("pageSize", "10")
                        .header("Authorization", bearer(author)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].ticketId").value(ticket.getId()))
                .andExpect(jsonPath("$.data[0].mentionedUsers[0].fullName").value(mentioned.getFullName()))
                .andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.page").value(1));
    }

    @Test
    void deletedMentionedUserIsIgnoredInTicketCommentMentionResponses() throws Exception {
        String suffix = suffix();
        User author = seedUser("mention-delete-author-" + suffix, Role.DEVELOPER);
        User mentioned = seedUser("mention-delete-user-" + suffix, Role.DEVELOPER);
        Project project = seedProject("Mention Delete " + suffix, author);
        Ticket ticket = seedTicket(project, author, "Mention deleted user ticket");
        String token = bearer(author);

        mockMvc.perform(post("/tickets/" + ticket.getId() + "/comments")
                        .header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "authorId": %d,
                                  "content": "Hello @%s"
                                }
                                """.formatted(author.getId(), mentioned.getUsername())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mentionedUsers[0].id").value(mentioned.getId()));

        mockMvc.perform(delete("/users/" + mentioned.getId())
                        .header("Authorization", token))
                .andExpect(status().isOk());

        mockMvc.perform(get("/users/" + mentioned.getId() + "/mentions")
                        .param("page", "1")
                        .param("pageSize", "10")
                        .header("Authorization", token))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("User not found"));

        mockMvc.perform(get("/tickets/" + ticket.getId() + "/comments")
                        .header("Authorization", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].mentionedUsers").isEmpty());
    }

    @Test
    void deletingCurrentUserWritesSystemLogoutAuditEvent() throws Exception {
        String suffix = suffix();
        User user = seedUser("self-delete-" + suffix, Role.DEVELOPER);
        User auditor = seedUser("self-delete-auditor-" + suffix, Role.ADMIN);

        mockMvc.perform(delete("/users/" + user.getId())
                        .header("Authorization", bearer(user)))
                .andExpect(status().isOk())
                .andExpect(content().string(""));

        mockMvc.perform(get("/audit-logs")
                        .param("action", "LOGOUT")
                        .param("entityType", "AUTH")
                        .param("entityId", user.getId().toString())
                        .header("Authorization", bearer(auditor)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].actor").value("SYSTEM"))
                .andExpect(jsonPath("$[0].performedBy").value(user.getId()))
                .andExpect(jsonPath("$[0].entityType").value("AUTH"))
                .andExpect(jsonPath("$[0].action").value("LOGOUT"));
    }

    @Test
    void ticketPatchRejectsBlankTitleAndDescription() throws Exception {
        String suffix = suffix();
        User user = seedUser("patch-" + suffix, Role.DEVELOPER);
        Project project = seedProject("Patch " + suffix, user);
        Ticket ticket = seedTicket(project, user, "Patch ticket");
        String token = bearer(user);

        mockMvc.perform(patch("/tickets/" + ticket.getId())
                        .header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "title": "   "
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Ticket title must not be blank"));

        mockMvc.perform(patch("/tickets/" + ticket.getId())
                        .header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "description": "   "
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Ticket description must not be blank"));

        mockMvc.perform(patch("/tickets/" + ticket.getId())
                        .header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "title": "Updated patch ticket"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(content().string(""));

        mockMvc.perform(get("/tickets/" + ticket.getId())
                        .header("Authorization", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Updated patch ticket"));
    }

    @Test
    void invalidUserRoleNamesAllowedReadmeValues() throws Exception {
        String suffix = suffix();
        User admin = seedUser("role-admin-" + suffix, Role.ADMIN);

        mockMvc.perform(post("/users")
                        .header("Authorization", bearer(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "username": "bad-role-%s",
                                  "email": "bad-role-%s@example.com",
                                  "fullName": "Bad Role",
                                  "role": "MANAGER"
                                }
                                """.formatted(suffix, suffix)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("role must be one of: ADMIN, DEVELOPER"));
    }

    @Test
    void readmeNestedCommentRoutesRequireCommentToBelongToPathTicket() throws Exception {
        String suffix = suffix();
        User user = seedUser("comment-parent-user-" + suffix, Role.DEVELOPER);
        Project project = seedProject("Comment Parent " + suffix, user);
        Ticket ticket = seedTicket(project, user, "Comment parent ticket");
        Ticket otherTicket = seedTicket(project, user, "Other comment parent ticket");
        String token = bearer(user);

        MvcResult created = mockMvc.perform(post("/tickets/" + ticket.getId() + "/comments")
                        .header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "authorId": %d,
                                  "content": "Original comment"
                                }
                                """.formatted(user.getId())))
                .andExpect(status().isOk())
                .andReturn();
        String commentId = com.jayway.jsonpath.JsonPath.read(created.getResponse().getContentAsString(), "$.id").toString();
        long missingTicketId = 999_999_901L;

        mockMvc.perform(patch("/tickets/" + missingTicketId + "/comments/" + commentId)
                        .header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "content": "Missing parent update"
                                }
                                """))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Ticket not found"));

        mockMvc.perform(delete("/tickets/" + missingTicketId + "/comments/" + commentId)
                        .header("Authorization", token))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Ticket not found"));

        mockMvc.perform(patch("/tickets/" + otherTicket.getId() + "/comments/" + commentId)
                        .header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "content": "Wrong parent update"
                                }
                                """))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Comment not found"));

        mockMvc.perform(delete("/tickets/" + otherTicket.getId() + "/comments/" + commentId)
                        .header("Authorization", token))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Comment not found"));

        mockMvc.perform(get("/tickets/" + ticket.getId() + "/comments")
                        .header("Authorization", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].content").value("Original comment"));
    }

    @Test
    void readmeNestedAttachmentDeleteRequiresAttachmentToBelongToPathTicket() throws Exception {
        String suffix = suffix();
        User user = seedUser("attachment-parent-user-" + suffix, Role.DEVELOPER);
        Project project = seedProject("Attachment Parent " + suffix, user);
        Ticket ticket = seedTicket(project, user, "Attachment parent ticket");
        Ticket otherTicket = seedTicket(project, user, "Other attachment parent ticket");
        String token = bearer(user);
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "note.txt",
                "text/plain",
                "hello".getBytes()
        );

        MvcResult uploaded = mockMvc.perform(multipart("/tickets/" + ticket.getId() + "/attachments")
                        .file(file)
                        .header("Authorization", token))
                .andExpect(status().isOk())
                .andReturn();
        String attachmentId = com.jayway.jsonpath.JsonPath.read(uploaded.getResponse().getContentAsString(), "$.id").toString();
        long missingTicketId = 999_999_902L;

        mockMvc.perform(delete("/tickets/" + missingTicketId + "/attachments/" + attachmentId)
                        .header("Authorization", token))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Ticket not found"));

        mockMvc.perform(delete("/tickets/" + otherTicket.getId() + "/attachments/" + attachmentId)
                        .header("Authorization", token))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Attachment not found"));

        assertThat(attachmentRepository.findActiveIdsByTicketIdIncludingDeletedTicket(ticket.getId()))
                .contains(Long.parseLong(attachmentId));
    }

    @Test
    void readmeNestedDependencyRoutesReturnSpecificParentAndDependencyErrors() throws Exception {
        String suffix = suffix();
        User user = seedUser("dependency-parent-user-" + suffix, Role.DEVELOPER);
        Project project = seedProject("Dependency Parent " + suffix, user);
        Ticket ticket = seedTicket(project, user, "Dependency parent ticket");
        Ticket blocker = seedTicket(project, user, "Dependency blocker ticket");
        String token = bearer(user);
        long missingTicketId = 999_999_903L;
        long missingBlockerId = 999_999_904L;

        mockMvc.perform(post("/tickets/" + missingTicketId + "/dependencies")
                        .header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "blockedBy": %d
                                }
                                """.formatted(blocker.getId())))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Ticket not found"));

        mockMvc.perform(post("/tickets/" + ticket.getId() + "/dependencies")
                        .header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "blockedBy": %d
                                }
                                """.formatted(missingBlockerId)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Blocked ticket not found"));

        mockMvc.perform(delete("/tickets/" + missingTicketId + "/dependencies/" + blocker.getId())
                        .header("Authorization", token))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Ticket not found"));

        mockMvc.perform(delete("/tickets/" + ticket.getId() + "/dependencies/" + missingBlockerId)
                        .header("Authorization", token))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Blocked ticket not found"));

        mockMvc.perform(delete("/tickets/" + ticket.getId() + "/dependencies/" + blocker.getId())
                        .header("Authorization", token))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Ticket dependency not found"));
    }

    @Test
    void unknownMentionUserReturnsInformativeError() throws Exception {
        String suffix = suffix();
        User user = seedUser("unknown-mention-author-" + suffix, Role.DEVELOPER);
        Project project = seedProject("Unknown Mention " + suffix, user);
        Ticket ticket = seedTicket(project, user, "Unknown mention ticket");

        mockMvc.perform(post("/tickets/" + ticket.getId() + "/comments")
                        .header("Authorization", bearer(user))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "authorId": %d,
                                  "content": "Hello @missing_%s"
                                }
                                """.formatted(user.getId(), suffix)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Unknown mentioned user: missing_" + suffix));
    }

    @Test
    void deletedTicketsCanBeListedForSoftDeletedProject() throws Exception {
        String suffix = suffix();
        User admin = seedUser("deleted-project-admin-" + suffix, Role.ADMIN);
        Project project = seedProject("Deleted Project Ticket List " + suffix, admin);
        Ticket ticket = seedTicket(project, admin, "Deleted project list ticket");
        String token = bearer(admin);

        mockMvc.perform(delete("/projects/" + project.getId())
                        .header("Authorization", token))
                .andExpect(status().isOk());

        mockMvc.perform(get("/tickets/deleted")
                        .param("projectId", project.getId().toString())
                        .header("Authorization", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(ticket.getId()))
                .andExpect(jsonPath("$[0].projectId").value(project.getId()));
    }

    @Test
    void deletingProjectOwnerSoftDeletesOwnedProjectsAndChildren() throws Exception {
        String suffix = suffix();
        User admin = seedUser("owner-delete-admin-" + suffix, Role.ADMIN);
        User owner = seedUser("owner-delete-owner-" + suffix, Role.DEVELOPER);
        Project project = seedProject("Owner Delete Cascade " + suffix, owner);
        Ticket ticket = seedTicket(project, owner, "Owner delete ticket");
        String token = bearer(admin);

        mockMvc.perform(post("/tickets/" + ticket.getId() + "/comments")
                        .header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "authorId": %d,
                                  "content": "Owner cascade comment"
                                }
                                """.formatted(owner.getId())))
                .andExpect(status().isOk());

        mockMvc.perform(delete("/users/" + owner.getId())
                        .header("Authorization", token))
                .andExpect(status().isOk());

        mockMvc.perform(get("/projects/" + project.getId())
                        .header("Authorization", token))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/tickets/" + ticket.getId())
                        .header("Authorization", token))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/tickets/deleted")
                        .param("projectId", project.getId().toString())
                        .header("Authorization", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(ticket.getId()));

        assertThat(systemAuditCount(AuditAction.DELETE, AuditableEntityType.TICKET, ticket.getId())).isEqualTo(1);
        assertThat(systemAuditCount(AuditAction.DELETE, AuditableEntityType.COMMENT, null)).isGreaterThanOrEqualTo(1);
    }

    @Test
    void mentionCreateAndRemovalAreAudited() throws Exception {
        String suffix = suffix();
        User author = seedUser("mention-audit-author-" + suffix, Role.DEVELOPER);
        User mentioned = seedUser("mention-audit-user-" + suffix, Role.DEVELOPER);
        Project project = seedProject("Mention Audit " + suffix, author);
        Ticket ticket = seedTicket(project, author, "Mention audit ticket");
        String token = bearer(author);
        long createMentionsBefore = auditCount(AuditAction.CREATE, AuditableEntityType.MENTION);
        long deleteMentionsBefore = auditCount(AuditAction.DELETE, AuditableEntityType.MENTION);

        MvcResult created = mockMvc.perform(post("/tickets/" + ticket.getId() + "/comments")
                        .header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "authorId": %d,
                                  "content": "Hello @%s"
                                }
                                """.formatted(author.getId(), mentioned.getUsername())))
                .andExpect(status().isOk())
                .andReturn();
        String commentId = com.jayway.jsonpath.JsonPath.read(created.getResponse().getContentAsString(), "$.id").toString();

        assertThat(auditCount(AuditAction.CREATE, AuditableEntityType.MENTION)).isEqualTo(createMentionsBefore + 1);

        mockMvc.perform(patch("/tickets/" + ticket.getId() + "/comments/" + commentId)
                        .header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "content": "Mention removed"
                                }
                                """))
                .andExpect(status().isOk());

        assertThat(auditCount(AuditAction.DELETE, AuditableEntityType.MENTION)).isEqualTo(deleteMentionsBefore + 1);
    }

    private User seedUser(String username, Role role) {
        User user = new User();
        user.setUsername(username);
        user.setEmail(username + "@example.com");
        user.setFullName(username);
        user.setRole(role);
        user.setActive(true);
        return userRepository.saveAndFlush(user);
    }

    private Project seedProject(String name, User owner) {
        Project project = new Project();
        project.setKey(("P-" + UUID.randomUUID()).substring(0, 30).toUpperCase(Locale.ROOT));
        project.setName(name);
        project.setDescription("Seed project");
        project.setOwner(owner);
        return projectRepository.saveAndFlush(project);
    }

    private ProjectMember seedProjectMember(Project project, User user) {
        ProjectMember member = new ProjectMember();
        member.setProject(project);
        member.setUser(user);
        return projectMemberRepository.saveAndFlush(member);
    }

    private Ticket seedTicket(Project project, User reporter, String title) {
        Ticket ticket = new Ticket();
        ticket.setProject(project);
        ticket.setReporter(reporter);
        ticket.setTitle(title);
        ticket.setDescription("Seed ticket");
        ticket.setStatus(TicketStatus.TODO);
        ticket.setPriority(TicketPriority.MEDIUM);
        ticket.setType(TicketType.FEATURE);
        return ticketRepository.saveAndFlush(ticket);
    }

    private long removeDependencyAuditCount(Long entityId) {
        return auditLogRepository.findAll().stream()
                .filter(log -> log.getAction() == AuditAction.REMOVE_DEPENDENCY)
                .filter(log -> entityId.equals(log.getEntityId()))
                .count();
    }

    private long systemAuditCount(AuditAction action, AuditableEntityType entityType, Long entityId) {
        return auditLogRepository.findAll().stream()
                .filter(log -> log.getActorType() == AuditActorType.SYSTEM)
                .filter(log -> log.getAction() == action)
                .filter(log -> log.getEntityType() == entityType)
                .filter(log -> entityId == null || entityId.equals(log.getEntityId()))
                .count();
    }

    private List<Long> systemAuditPerformedBy(AuditAction action, AuditableEntityType entityType, Long entityId) {
        return auditLogRepository.findAll().stream()
                .filter(log -> log.getActorType() == AuditActorType.SYSTEM)
                .filter(log -> log.getAction() == action)
                .filter(log -> log.getEntityType() == entityType)
                .filter(log -> entityId == null || entityId.equals(log.getEntityId()))
                .map(log -> log.getActorId())
                .toList();
    }

    private long auditCount(AuditAction action, AuditableEntityType entityType) {
        return auditLogRepository.findAll().stream()
                .filter(log -> log.getAction() == action)
                .filter(log -> log.getEntityType() == entityType)
                .count();
    }

    private String bearer(User user) {
        return "Bearer " + jwtTokenService.generateToken(user);
    }

    private String suffix() {
        return UUID.randomUUID().toString().substring(0, 8).toLowerCase(Locale.ROOT);
    }
}
