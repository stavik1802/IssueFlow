package com.att.tdp.issueflow.project;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.att.tdp.issueflow.project.dto.WorkloadResponse;
import com.att.tdp.issueflow.ticket.TicketRepository;
import com.att.tdp.issueflow.user.Role;
import com.att.tdp.issueflow.user.User;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class WorkloadServiceTest {

    @Mock
    private ProjectRepository projectRepository;

    @Mock
    private ProjectMemberRepository projectMemberRepository;

    @Mock
    private TicketRepository ticketRepository;

    private WorkloadService workloadService;

    @BeforeEach
    void setUp() {
        workloadService = new WorkloadService(projectRepository, projectMemberRepository, ticketRepository);
    }

    @Test
    void returnsAllProjectMembersSortedByOpenTicketCount() {
        Project project = project();
        User admin = user(1L, "admin", Role.ADMIN);
        User alice = user(2L, "alice", Role.DEVELOPER);
        User bob = user(3L, "bob", Role.DEVELOPER);
        when(projectRepository.findById(10L)).thenReturn(Optional.of(project));
        when(projectMemberRepository.findByProjectId(10L)).thenReturn(List.of(
                member(project, admin),
                member(project, alice),
                member(project, bob)
        ));
        when(ticketRepository.countOpenTicketsByAssignee(10L)).thenReturn(List.of(count(2L, 4), count(3L, 1)));

        WorkloadResponse response = workloadService.getWorkload(10L);

        assertThat(response.members())
                .extracting(WorkloadResponse.MemberWorkload::userId)
                .containsExactly(1L, 3L, 2L);
        assertThat(response.members())
                .extracting(WorkloadResponse.MemberWorkload::openTicketCount)
                .containsExactly(0L, 1L, 4L);
    }

    private static TicketRepository.AssigneeOpenTicketCount count(Long userId, long count) {
        return new TicketRepository.AssigneeOpenTicketCount() {
            @Override
            public Long getUserId() {
                return userId;
            }

            @Override
            public long getOpenTicketCount() {
                return count;
            }
        };
    }

    private static ProjectMember member(Project project, User user) {
        ProjectMember member = new ProjectMember();
        member.setProject(project);
        member.setUser(user);
        return member;
    }

    private static Project project() {
        Project project = new Project();
        project.setId(10L);
        project.setName("Project");
        project.setKey("PROJECT");
        return project;
    }

    private static User user(Long id, String username, Role role) {
        User user = new User();
        user.setId(id);
        user.setUsername(username);
        user.setEmail(username + "@example.com");
        user.setFullName(username);
        user.setRole(role);
        return user;
    }
}
