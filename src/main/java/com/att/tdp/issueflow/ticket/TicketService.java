package com.att.tdp.issueflow.ticket;

import com.att.tdp.issueflow.audit.AuditAction;
import com.att.tdp.issueflow.audit.AuditEventPublisher;
import com.att.tdp.issueflow.audit.AuditableEntityType;
import com.att.tdp.issueflow.attachment.AttachmentRepository;
import com.att.tdp.issueflow.comment.CommentMapper;
import com.att.tdp.issueflow.comment.CommentRepository;
import com.att.tdp.issueflow.common.exception.BadRequestException;
import com.att.tdp.issueflow.common.exception.ForbiddenException;
import com.att.tdp.issueflow.common.exception.ConflictException;
import com.att.tdp.issueflow.common.exception.NotFoundException;
import com.att.tdp.issueflow.project.Project;
import com.att.tdp.issueflow.project.ProjectRepository;
import com.att.tdp.issueflow.security.auth.CurrentUser;
import com.att.tdp.issueflow.ticket.dto.CreateTicketRequest;
import com.att.tdp.issueflow.ticket.dto.TicketResponse;
import com.att.tdp.issueflow.ticket.dto.UpdateTicketRequest;
import com.att.tdp.issueflow.user.Role;
import com.att.tdp.issueflow.user.User;
import com.att.tdp.issueflow.user.UserRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TicketService {

    private final TicketRepository ticketRepository;
    private final ProjectRepository projectRepository;
    private final UserRepository userRepository;
    private final TicketAssignmentService ticketAssignmentService;
    private final TicketDependencyService ticketDependencyService;
    private final TicketDependencyRepository ticketDependencyRepository;
    private final TicketLifecyclePolicy ticketLifecyclePolicy;
    private final TicketMapper ticketMapper;
    private final CommentRepository commentRepository;
    private final CommentMapper commentMapper;
    private final AttachmentRepository attachmentRepository;
    private final Clock clock;
    private final AuditEventPublisher auditEventPublisher;

    public TicketService(
            TicketRepository ticketRepository,
            ProjectRepository projectRepository,
            UserRepository userRepository,
            TicketAssignmentService ticketAssignmentService,
            TicketDependencyService ticketDependencyService,
            TicketDependencyRepository ticketDependencyRepository,
            TicketLifecyclePolicy ticketLifecyclePolicy,
            TicketMapper ticketMapper,
            CommentRepository commentRepository,
            CommentMapper commentMapper,
            AttachmentRepository attachmentRepository,
            Clock clock,
            AuditEventPublisher auditEventPublisher
    ) {
        this.ticketRepository = ticketRepository;
        this.projectRepository = projectRepository;
        this.userRepository = userRepository;
        this.ticketAssignmentService = ticketAssignmentService;
        this.ticketDependencyService = ticketDependencyService;
        this.ticketDependencyRepository = ticketDependencyRepository;
        this.ticketLifecyclePolicy = ticketLifecyclePolicy;
        this.ticketMapper = ticketMapper;
        this.commentRepository = commentRepository;
        this.commentMapper = commentMapper;
        this.attachmentRepository = attachmentRepository;
        this.clock = clock;
        this.auditEventPublisher = auditEventPublisher;
    }

    @Transactional
    public TicketResponse create(CreateTicketRequest request) {
        return create(request, null);
    }

    @Transactional
    public TicketResponse create(CreateTicketRequest request, CurrentUser currentUser) {
        Project project = projectRepository.findById(request.projectId())
                .orElseThrow(() -> new NotFoundException("Project not found"));
        Long reporterId = project.getOwner().getId();
        if (reporterId == null) {
            throw new NotFoundException("Ticket reporter not found");
        }
        User reporter = userRepository.findById(reporterId)
                .orElseThrow(() -> new NotFoundException("Ticket reporter not found"));
        User assignee = resolveAssignee(request.assigneeId());

        Ticket ticket = ticketMapper.toEntity(request, project, reporter, assignee);
        if (assignee == null) {
            ticketAssignmentService.autoAssignIfNeeded(ticket);
        }
        if (existsDuplicateTicket(ticket, project, reporter)) {
            throw new ConflictException("Duplicate ticket");
        }
        Ticket savedTicket = ticketRepository.save(ticket);
        if (assignee == null) {
            ticketAssignmentService.recordAutoAssignment(savedTicket, currentUser == null ? reporter.getId() : currentUser.id());
        }
        TicketResponse response = ticketMapper.toResponse(savedTicket);
        auditEventPublisher.userAction(
                reporter.getId(),
                AuditAction.CREATE,
                AuditableEntityType.TICKET,
                response.id(),
                null,
                response
        );
        return response;
    }

    @Transactional(readOnly = true)
    public TicketResponse getById(Long id) {
        return ticketMapper.toResponse(findActiveTicket(id));
    }

    @Transactional(readOnly = true)
    public List<TicketResponse> getByProject(Long projectId) {
        if (!projectRepository.existsById(projectId)) {
            throw new NotFoundException("Project not found");
        }
        return ticketRepository.findByProjectIdOrderByCreatedAtDesc(projectId).stream()
                .map(ticketMapper::toResponse)
                .toList();
    }

    @Transactional
    public TicketResponse update(Long id, UpdateTicketRequest request) {
        return update(id, request, null);
    }

    @Transactional
    public TicketResponse update(Long id, UpdateTicketRequest request, CurrentUser currentUser) {
        Ticket ticket = findActiveTicketForUpdate(id);
        if (request.assigneeId() == null
                && request.title() == null
                && request.description() == null
                && request.priority() == null
                && request.status() == null
                && request.dueDate() == null) {
            throw new BadRequestException("At least one ticket field must be provided");
        }
        ticketLifecyclePolicy.validateUpdateAllowed(ticket);
        ticketLifecyclePolicy.validateStatusTransition(ticket.getStatus(), request.status());
        validateVersion(ticket.getVersion(), request.version());
        TicketResponse oldValue = ticketMapper.toResponse(ticket);

        if (request.assigneeId() != null) {
            ticket.setAssignee(resolveAssignee(request.assigneeId()));
        }
        if (request.title() != null) {
            if (request.title().isBlank()) {
                throw new BadRequestException("Ticket title must not be blank");
            }
            ticket.setTitle(request.title().trim());
        }
        if (request.description() != null) {
            if (request.description().isBlank()) {
                throw new BadRequestException("Ticket description must not be blank");
            }
            ticket.setDescription(ticketMapper.trimToNull(request.description()));
        }
        if (request.priority() != null) {
            ticket.setPriority(request.priority());
            ticket.setOverdue(false);
        }
        if (request.status() != null) {
            if (request.status() == TicketStatus.DONE) {
                ticketDependencyService.validateNoUnresolvedBlockers(ticket.getId());
            }
            ticket.setStatus(request.status());
            if (request.status() == TicketStatus.DONE) {
                ticket.setResolvedAt(Instant.now(clock));
            }
        }
        if (request.dueDate() != null) {
            ticket.setDueAt(request.dueDate());
        }

        TicketResponse response = ticketMapper.toResponse(ticket);
        auditEventPublisher.userAction(
                currentUser == null ? null : currentUser.id(),
                AuditAction.UPDATE,
                AuditableEntityType.TICKET,
                id,
                oldValue,
                response
        );
        return response;
    }

    @Transactional
    public void delete(Long id) {
        delete(id, null);
    }

    @Transactional
    public void delete(Long id, CurrentUser currentUser) {
        Ticket ticket = findActiveTicket(id);
        TicketResponse oldValue = ticketMapper.toResponse(ticket);
        softDeleteTicketChildrenAsSystem(ticket.getId(), currentUser == null ? null : currentUser.id());
        ticketRepository.delete(ticket);
        auditEventPublisher.userAction(
                currentUser == null ? null : currentUser.id(),
                AuditAction.DELETE,
                AuditableEntityType.TICKET,
                id,
                oldValue,
                null
        );
    }

    @Transactional
    public TicketResponse restore(Long id, CurrentUser currentUser) {
        requireAdmin(currentUser);
        Ticket ticket = ticketRepository.findIncludingDeletedById(id)
                .orElseThrow(() -> new NotFoundException("Ticket not found"));
        if (!ticket.isDeleted()) {
            return ticketMapper.toResponse(ticket);
        }
        TicketResponse oldValue = ticketMapper.toResponse(ticket);
        int restored = ticketRepository.restoreById(id);
        if (restored == 0) {
            throw new NotFoundException("Ticket not found");
        }
        restoreTicketChildrenAsSystem(id, currentUser.id());
        TicketResponse response = ticketMapper.toResponse(ticketRepository.findIncludingDeletedById(id)
                .orElseThrow(() -> new NotFoundException("Ticket not found")));
        auditEventPublisher.userAction(
                currentUser.id(),
                AuditAction.RESTORE,
                AuditableEntityType.TICKET,
                id,
                oldValue,
                response
        );
        return response;
    }

    @Transactional(readOnly = true)
    public List<TicketResponse> getDeletedByProject(Long projectId, CurrentUser currentUser) {
        requireAdmin(currentUser);
        if (projectRepository.findIncludingDeletedById(projectId).isEmpty()) {
            throw new NotFoundException("Project not found");
        }
        return ticketRepository.findDeletedByProjectId(projectId).stream()
                .map(ticketMapper::toResponse)
                .toList();
    }

    private Ticket findActiveTicket(Long id) {
        return ticketRepository.findActiveWithActiveProjectById(id)
                .orElseThrow(() -> new NotFoundException("Ticket not found"));
    }

    private Ticket findActiveTicketForUpdate(Long id) {
        return ticketRepository.findActiveWithActiveProjectByIdForUpdate(id)
                .orElseThrow(() -> new NotFoundException("Ticket not found"));
    }

    private User resolveAssignee(Long assigneeId) {
        if (assigneeId == null) {
            return null;
        }
        return userRepository.findById(assigneeId)
                .orElseThrow(() -> new NotFoundException("Ticket assignee not found"));
    }

    private boolean existsDuplicateTicket(Ticket ticket, Project project, User reporter) {
        Long assigneeId = ticket.getAssignee() == null ? null : ticket.getAssignee().getId();
        if (ticket.getDueAt() == null) {
            return ticketRepository.existsDuplicateActiveTicketWithoutDueAt(
                    project.getId(),
                    reporter.getId(),
                    assigneeId,
                    ticket.getTitle(),
                    ticket.getDescription(),
                    ticket.getStatus().name(),
                    ticket.getPriority().name(),
                    ticket.getType().name()
            );
        }
        return ticketRepository.existsDuplicateActiveTicketWithDueAt(
                project.getId(),
                reporter.getId(),
                assigneeId,
                ticket.getTitle(),
                ticket.getDescription(),
                ticket.getStatus().name(),
                ticket.getPriority().name(),
                ticket.getType().name(),
                ticket.getDueAt()
        );
    }

    private void softDeleteTicketChildrenAsSystem(Long ticketId, Long actorId) {
        List<Long> commentIds = commentRepository.findActiveIdsByTicketIdIncludingDeletedTicket(ticketId);
        List<Long> attachmentIds = attachmentRepository.findActiveIdsByTicketIdIncludingDeletedTicket(ticketId);
        List<Long> dependencyIds = ticketDependencyRepository.findActiveIdsTouchingTicketId(ticketId);
        commentRepository.softDeleteActiveByTicketIdAsSystem(ticketId);
        attachmentRepository.softDeleteActiveByTicketIdAsSystem(ticketId);
        ticketDependencyRepository.softDeleteActiveTouchingTicketIdAsSystem(ticketId);
        commentIds.forEach(commentId -> auditEventPublisher.systemAction(
                actorId,
                AuditAction.DELETE,
                AuditableEntityType.COMMENT,
                commentId,
                null,
                null
        ));
        attachmentIds.forEach(attachmentId -> auditEventPublisher.systemAction(
                actorId,
                AuditAction.DELETE_ATTACHMENT,
                AuditableEntityType.ATTACHMENT,
                attachmentId,
                null,
                null
        ));
        dependencyIds.forEach(dependencyId -> auditEventPublisher.systemAction(
                actorId,
                AuditAction.DELETE,
                AuditableEntityType.TICKET_DEPENDENCY,
                dependencyId,
                null,
                null
        ));
    }

    private void restoreTicketChildrenAsSystem(Long ticketId, Long actorId) {
        List<Long> commentIds = commentRepository.findSystemDeletedIdsByTicketId(ticketId);
        List<Long> attachmentIds = attachmentRepository.findSystemDeletedIdsByTicketId(ticketId);
        List<Long> dependencyIds = ticketDependencyRepository.findSystemDeletedIdsTouchingTicketId(ticketId);
        commentRepository.restoreSystemDeletedByTicketId(ticketId);
        attachmentRepository.restoreSystemDeletedByTicketId(ticketId);
        ticketDependencyRepository.restoreSystemDeletedTouchingTicketId(ticketId);
        commentIds.forEach(commentId -> auditEventPublisher.systemAction(
                actorId,
                AuditAction.RESTORE,
                AuditableEntityType.COMMENT,
                commentId,
                null,
                null
        ));
        attachmentIds.forEach(attachmentId -> auditEventPublisher.systemAction(
                actorId,
                AuditAction.RESTORE,
                AuditableEntityType.ATTACHMENT,
                attachmentId,
                null,
                null
        ));
        dependencyIds.forEach(dependencyId -> auditEventPublisher.systemAction(
                actorId,
                AuditAction.RESTORE,
                AuditableEntityType.TICKET_DEPENDENCY,
                dependencyId,
                null,
                null
        ));
    }

    private void validateVersion(long currentVersion, Long requestedVersion) {
        if (requestedVersion != null && requestedVersion != currentVersion) {
            throw new ConflictException("Ticket update conflict");
        }
    }

    private void requireAdmin(CurrentUser currentUser) {
        if (currentUser == null || currentUser.role() != Role.ADMIN) {
            throw new ForbiddenException("Admin role is required");
        }
    }
}
