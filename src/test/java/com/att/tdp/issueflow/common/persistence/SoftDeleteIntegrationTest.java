package com.att.tdp.issueflow.common.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import com.att.tdp.issueflow.project.Project;
import com.att.tdp.issueflow.project.ProjectRepository;
import com.att.tdp.issueflow.ticket.Ticket;
import com.att.tdp.issueflow.ticket.TicketPriority;
import com.att.tdp.issueflow.ticket.TicketRepository;
import com.att.tdp.issueflow.ticket.TicketStatus;
import com.att.tdp.issueflow.ticket.TicketType;
import com.att.tdp.issueflow.user.Role;
import com.att.tdp.issueflow.user.User;
import com.att.tdp.issueflow.user.UserRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@Transactional
class SoftDeleteIntegrationTest {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ProjectRepository projectRepository;

    @Autowired
    private TicketRepository ticketRepository;

    @PersistenceContext
    private EntityManager entityManager;

    @Test
    void projectDeleteSoftDeletesAndRestoreMakesItVisibleAgain() {
        Project project = projectRepository.saveAndFlush(project(user("project-owner")));
        Long projectId = project.getId();

        projectRepository.delete(project);
        flushAndClear();

        assertThat(projectRepository.findById(projectId)).isEmpty();
        Project deletedProject = projectRepository.findIncludingDeletedById(projectId).orElseThrow();
        assertThat(deletedProject.getDeletedAt()).isNotNull();

        projectRepository.restoreById(projectId);
        flushAndClear();

        Project restoredProject = projectRepository.findById(projectId).orElseThrow();
        assertThat(restoredProject.getDeletedAt()).isNull();
    }

    @Test
    void ticketDeleteSoftDeletesAndRestoreMakesItVisibleAgain() {
        User reporter = user("ticket-reporter");
        Project project = projectRepository.saveAndFlush(project(reporter));
        Ticket ticket = ticketRepository.saveAndFlush(ticket(project, reporter));
        Long ticketId = ticket.getId();

        ticketRepository.delete(ticket);
        flushAndClear();

        assertThat(ticketRepository.findById(ticketId)).isEmpty();
        Ticket deletedTicket = ticketRepository.findIncludingDeletedById(ticketId).orElseThrow();
        assertThat(deletedTicket.getDeletedAt()).isNotNull();

        ticketRepository.restoreById(ticketId);
        flushAndClear();

        Ticket restoredTicket = ticketRepository.findById(ticketId).orElseThrow();
        assertThat(restoredTicket.getDeletedAt()).isNull();
    }

    private void flushAndClear() {
        entityManager.flush();
        entityManager.clear();
    }

    private Project project(User owner) {
        User savedOwner = userRepository.saveAndFlush(owner);
        Project project = new Project();
        project.setKey("P-" + UUID.randomUUID().toString().substring(0, 8));
        project.setName("Persistence Project");
        project.setDescription("Soft delete integration test");
        project.setOwner(savedOwner);
        return project;
    }

    private static Ticket ticket(Project project, User reporter) {
        Ticket ticket = new Ticket();
        ticket.setProject(project);
        ticket.setReporter(reporter);
        ticket.setTitle("Persistence ticket");
        ticket.setDescription("Soft delete integration test");
        ticket.setStatus(TicketStatus.TODO);
        ticket.setPriority(TicketPriority.MEDIUM);
        ticket.setType(TicketType.FEATURE);
        return ticket;
    }

    private static User user(String prefix) {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        User user = new User();
        user.setUsername(prefix + "-" + suffix);
        user.setEmail(prefix + "-" + suffix + "@example.com");
        user.setFullName("Persistence Test User");
        user.setRole(Role.DEVELOPER);
        user.setActive(true);
        return user;
    }
}
