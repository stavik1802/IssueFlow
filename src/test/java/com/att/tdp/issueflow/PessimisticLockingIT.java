package com.att.tdp.issueflow;

import static org.assertj.core.api.Assertions.assertThat;

import com.att.tdp.issueflow.comment.Comment;
import com.att.tdp.issueflow.comment.CommentRepository;
import com.att.tdp.issueflow.comment.CommentService;
import com.att.tdp.issueflow.comment.dto.UpdateCommentRequest;
import com.att.tdp.issueflow.project.Project;
import com.att.tdp.issueflow.project.ProjectRepository;
import com.att.tdp.issueflow.security.auth.CurrentUser;
import com.att.tdp.issueflow.ticket.Ticket;
import com.att.tdp.issueflow.ticket.TicketPriority;
import com.att.tdp.issueflow.ticket.TicketRepository;
import com.att.tdp.issueflow.ticket.TicketService;
import com.att.tdp.issueflow.ticket.TicketStatus;
import com.att.tdp.issueflow.ticket.TicketType;
import com.att.tdp.issueflow.ticket.dto.UpdateTicketRequest;
import com.att.tdp.issueflow.user.Role;
import com.att.tdp.issueflow.user.User;
import com.att.tdp.issueflow.user.UserRepository;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@SpringBootTest
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:pessimistic-locking;MODE=PostgreSQL;DB_CLOSE_ON_EXIT=FALSE;LOCK_TIMEOUT=100",
        "spring.jpa.database=H2",
        "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect",
        "spring.jpa.show-sql=false"
})
class PessimisticLockingIT {

    @Autowired
    private TicketService ticketService;

    @Autowired
    private CommentService commentService;

    @Autowired
    private TicketRepository ticketRepository;

    @Autowired
    private CommentRepository commentRepository;

    @Autowired
    private ProjectRepository projectRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PlatformTransactionManager transactionManager;

    private ExecutorService executor;

    @AfterEach
    void shutdownExecutor() {
        if (executor != null) {
            executor.shutdownNow();
        }
    }

    @Test
    void ticketUpdateFailsFastWhenAnotherTransactionHoldsPessimisticWriteLock() throws Exception {
        TestData data = createTestData();
        TransactionTemplate transactionTemplate = new TransactionTemplate(transactionManager);
        executor = Executors.newFixedThreadPool(2);
        CountDownLatch lockAcquired = new CountDownLatch(1);
        CountDownLatch releaseLock = new CountDownLatch(1);
        CountDownLatch secondUpdateStarted = new CountDownLatch(1);

        Future<?> lockingUpdate = executor.submit(() -> transactionTemplate.executeWithoutResult(status -> {
            Ticket lockedTicket = ticketRepository.findActiveWithActiveProjectByIdForUpdate(data.ticketId())
                    .orElseThrow();
            lockedTicket.setTitle("first locked ticket update");
            lockAcquired.countDown();
            await(releaseLock);
        }));

        assertThat(lockAcquired.await(5, TimeUnit.SECONDS)).isTrue();

        Future<?> waitingUpdate = executor.submit(() -> {
            secondUpdateStarted.countDown();
            ticketService.update(data.ticketId(), new UpdateTicketRequest(
                    null,
                    "second rejected ticket update",
                    null,
                    null,
                    null,
                    null,
                    null
            ), data.developerUser());
        });

        assertThat(secondUpdateStarted.await(5, TimeUnit.SECONDS)).isTrue();
        try {
            assertFailsFastBecauseRowIsLocked(waitingUpdate);
        } finally {
            releaseLock.countDown();
            lockingUpdate.get(5, TimeUnit.SECONDS);
        }

        Ticket finalTicket = ticketRepository.findById(data.ticketId()).orElseThrow();
        assertThat(finalTicket.getTitle()).isEqualTo("first locked ticket update");
    }

    @Test
    void commentUpdateFailsFastWhenAnotherTransactionHoldsPessimisticWriteLock() throws Exception {
        TestData data = createTestData();
        TransactionTemplate transactionTemplate = new TransactionTemplate(transactionManager);
        executor = Executors.newFixedThreadPool(2);
        CountDownLatch lockAcquired = new CountDownLatch(1);
        CountDownLatch releaseLock = new CountDownLatch(1);
        CountDownLatch secondUpdateStarted = new CountDownLatch(1);

        Future<?> lockingUpdate = executor.submit(() -> transactionTemplate.executeWithoutResult(status -> {
            Comment lockedComment = commentRepository.findByIdForUpdate(data.commentId()).orElseThrow();
            lockedComment.setBody("first locked comment update");
            lockAcquired.countDown();
            await(releaseLock);
        }));

        assertThat(lockAcquired.await(5, TimeUnit.SECONDS)).isTrue();

        Future<?> waitingUpdate = executor.submit(() -> {
            secondUpdateStarted.countDown();
            commentService.update(data.commentId(), new UpdateCommentRequest(
                    "second rejected comment update",
                    null
            ), data.developerUser());
        });

        assertThat(secondUpdateStarted.await(5, TimeUnit.SECONDS)).isTrue();
        try {
            assertFailsFastBecauseRowIsLocked(waitingUpdate);
        } finally {
            releaseLock.countDown();
            lockingUpdate.get(5, TimeUnit.SECONDS);
        }

        Comment finalComment = commentRepository.findById(data.commentId()).orElseThrow();
        assertThat(finalComment.getBody()).isEqualTo("first locked comment update");
    }

    private TestData createTestData() {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        User user = new User();
        user.setUsername("lock-user-" + suffix);
        user.setEmail("lock-user-" + suffix + "@example.com");
        user.setFullName("Lock User");
        user.setRole(Role.DEVELOPER);
        user.setActive(true);
        user = userRepository.saveAndFlush(user);

        Project project = new Project();
        project.setKey("LOCK" + suffix.toUpperCase());
        project.setName("Lock Project " + suffix);
        project.setDescription("Project used by pessimistic locking tests");
        project.setOwner(user);
        project = projectRepository.saveAndFlush(project);

        Ticket ticket = new Ticket();
        ticket.setProject(project);
        ticket.setReporter(user);
        ticket.setAssignee(user);
        ticket.setTitle("Original ticket title");
        ticket.setDescription("Original ticket body");
        ticket.setStatus(TicketStatus.TODO);
        ticket.setPriority(TicketPriority.LOW);
        ticket.setType(TicketType.BUG);
        ticket = ticketRepository.saveAndFlush(ticket);

        Comment comment = new Comment();
        comment.setTicket(ticket);
        comment.setAuthor(user);
        comment.setBody("Original comment body");
        comment = commentRepository.saveAndFlush(comment);

        return new TestData(
                user.getId(),
                user.getUsername(),
                user.getEmail(),
                user.getFullName(),
                ticket.getId(),
                comment.getId()
        );
    }

    private static void await(CountDownLatch latch) {
        try {
            if (!latch.await(5, TimeUnit.SECONDS)) {
                throw new AssertionError("Timed out waiting for latch");
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new AssertionError("Interrupted while waiting for latch", exception);
        }
    }

    private static void assertFailsFastBecauseRowIsLocked(Future<?> update) throws InterruptedException {
        try {
            update.get(5, TimeUnit.SECONDS);
            throw new AssertionError("Expected update to fail fast because the row is locked");
        } catch (ExecutionException exception) {
            assertThat(exception.getCause()).isNotNull();
        } catch (TimeoutException exception) {
            throw new AssertionError("Expected update to fail fast instead of waiting for the lock", exception);
        }
    }

    private record TestData(
            Long userId,
            String username,
            String email,
            String fullName,
            Long ticketId,
            Long commentId
    ) {

        CurrentUser developerUser() {
            return new CurrentUser(userId, username, email, fullName, Role.DEVELOPER);
        }
    }
}
