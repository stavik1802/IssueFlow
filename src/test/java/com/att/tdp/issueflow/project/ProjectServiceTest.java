package com.att.tdp.issueflow.project;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.att.tdp.issueflow.audit.AuditEventPublisher;
import com.att.tdp.issueflow.attachment.AttachmentRepository;
import com.att.tdp.issueflow.comment.CommentMapper;
import com.att.tdp.issueflow.comment.CommentRepository;
import com.att.tdp.issueflow.common.exception.NotFoundException;
import com.att.tdp.issueflow.project.dto.CreateProjectRequest;
import com.att.tdp.issueflow.project.dto.ProjectResponse;
import com.att.tdp.issueflow.project.dto.UpdateProjectRequest;
import com.att.tdp.issueflow.security.auth.CurrentUser;
import com.att.tdp.issueflow.ticket.TicketDependencyRepository;
import com.att.tdp.issueflow.ticket.TicketMapper;
import com.att.tdp.issueflow.ticket.TicketRepository;
import com.att.tdp.issueflow.user.Role;
import com.att.tdp.issueflow.user.User;
import com.att.tdp.issueflow.user.UserRepository;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ProjectServiceTest {

    @Mock
    private ProjectRepository projectRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private TicketRepository ticketRepository;

    @Mock
    private TicketDependencyRepository ticketDependencyRepository;

    @Mock
    private CommentRepository commentRepository;

    @Mock
    private AttachmentRepository attachmentRepository;

    @Mock
    private AuditEventPublisher auditEventPublisher;

    private ProjectService projectService;

    @BeforeEach
    void setUp() {
        projectService = new ProjectService(
                projectRepository,
                userRepository,
                ticketRepository,
                new TicketMapper(),
                ticketDependencyRepository,
                commentRepository,
                new CommentMapper(),
                attachmentRepository,
                new ProjectMapper(),
                auditEventPublisher
        );
    }

    @Test
    void createsProject() {
        User owner = owner();
        when(userRepository.findById(1L)).thenReturn(Optional.of(owner));
        when(projectRepository.save(any(Project.class))).thenAnswer(invocation -> {
            Project project = invocation.getArgument(0);
            project.setId(10L);
            return project;
        });

        ProjectResponse response = projectService.create(new CreateProjectRequest(
                "IssueFlow API",
                "Backend project",
                1L
        ));

        ArgumentCaptor<Project> projectCaptor = ArgumentCaptor.forClass(Project.class);
        verify(projectRepository).save(projectCaptor.capture());
        Project savedProject = projectCaptor.getValue();

        assertThat(response.id()).isEqualTo(10L);
        assertThat(response.name()).isEqualTo("IssueFlow API");
        assertThat(response.description()).isEqualTo("Backend project");
        assertThat(response.ownerId()).isEqualTo(1L);
        assertThat(savedProject.getKey()).startsWith("ISSUEFLOW-API-");
        assertThat(savedProject.getMembers())
                .singleElement()
                .satisfies(member -> {
                    assertThat(member.getUser()).isSameAs(owner);
                    assertThat(member.getRole()).isEqualTo(ProjectMemberRole.OWNER);
                });
    }

    @Test
    void createFailsWhenOwnerDoesNotExist() {
        when(userRepository.findById(404L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> projectService.create(new CreateProjectRequest(
                "Missing Owner Project",
                null,
                404L
        )))
                .isInstanceOf(NotFoundException.class)
                .hasMessage("Project owner not found");
    }

    @Test
    void getsProject() {
        Project project = project();
        when(projectRepository.findById(10L)).thenReturn(Optional.of(project));

        ProjectResponse response = projectService.getById(10L);

        assertThat(response.id()).isEqualTo(10L);
        assertThat(response.name()).isEqualTo("IssueFlow API");
        assertThat(response.description()).isEqualTo("Backend project");
        assertThat(response.ownerId()).isEqualTo(1L);
    }

    @Test
    void updatesProject() {
        Project project = project();
        when(projectRepository.findById(10L)).thenReturn(Optional.of(project));

        ProjectResponse response = projectService.update(10L, new UpdateProjectRequest(
                "IssueFlow Platform",
                "Updated description"
        ));

        assertThat(project.getName()).isEqualTo("IssueFlow Platform");
        assertThat(project.getDescription()).isEqualTo("Updated description");
        assertThat(response.name()).isEqualTo("IssueFlow Platform");
    }

    @Test
    void updatesOnlyDescription() {
        Project project = project();
        when(projectRepository.findById(10L)).thenReturn(Optional.of(project));

        ProjectResponse response = projectService.update(10L, new UpdateProjectRequest(
                null,
                "Description only"
        ));

        assertThat(project.getName()).isEqualTo("IssueFlow API");
        assertThat(project.getDescription()).isEqualTo("Description only");
        assertThat(response.name()).isEqualTo("IssueFlow API");
        assertThat(response.description()).isEqualTo("Description only");
    }

    @Test
    void softDeleteHidesProjectFromNormalLookup() {
        Project project = project();
        when(projectRepository.findById(10L)).thenReturn(Optional.of(project), Optional.empty());

        projectService.delete(10L);

        verify(projectRepository).delete(project);
        assertThatThrownBy(() -> projectService.getById(10L))
                .isInstanceOf(NotFoundException.class)
                .hasMessage("Project not found");
    }

    @Test
    void restoresDeletedProject() {
        Project deletedProject = project();
        deletedProject.setDeletedAt(Instant.parse("2026-05-19T10:15:30Z"));
        Project restoredProject = project();

        when(projectRepository.findIncludingDeletedById(10L))
                .thenReturn(Optional.of(deletedProject), Optional.of(restoredProject));
        when(projectRepository.restoreById(10L)).thenReturn(1);

        ProjectResponse response = projectService.restore(10L, admin());

        verify(projectRepository).restoreById(10L);
        assertThat(response.id()).isEqualTo(10L);
        assertThat(response.name()).isEqualTo("IssueFlow API");
        assertThat(response.ownerId()).isEqualTo(1L);
    }

    @Test
    void listsDeletedProjects() {
        Project deletedProject = project();
        deletedProject.setDeletedAt(Instant.parse("2026-05-19T10:15:30Z"));
        when(projectRepository.findDeletedProjects()).thenReturn(List.of(deletedProject));

        List<ProjectResponse> response = projectService.getDeletedProjects(admin());

        assertThat(response)
                .singleElement()
                .satisfies(project -> {
                    assertThat(project.id()).isEqualTo(10L);
                    assertThat(project.name()).isEqualTo("IssueFlow API");
                    assertThat(project.description()).isEqualTo("Backend project");
                    assertThat(project.ownerId()).isEqualTo(1L);
                });
    }

    private static CurrentUser admin() {
        return new CurrentUser(99L, "admin", "admin@example.com", "Admin User", Role.ADMIN);
    }

    private static Project project() {
        User owner = owner();
        Project project = new Project();
        project.setId(10L);
        project.setKey("ISSUEFLOW-API-12345678");
        project.setName("IssueFlow API");
        project.setDescription("Backend project");
        project.setOwner(owner);

        ProjectMember member = new ProjectMember();
        member.setUser(owner);
        member.setRole(ProjectMemberRole.OWNER);
        project.addMember(member);

        return project;
    }

    private static User owner() {
        User user = new User();
        user.setId(1L);
        user.setUsername("owner");
        user.setEmail("owner@example.com");
        user.setFullName("Project Owner");
        user.setRole(Role.DEVELOPER);
        return user;
    }
}
