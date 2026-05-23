package com.att.tdp.issueflow.project;

import com.att.tdp.issueflow.audit.AuditAction;
import com.att.tdp.issueflow.audit.AuditEventPublisher;
import com.att.tdp.issueflow.audit.AuditableEntityType;
import com.att.tdp.issueflow.attachment.AttachmentRepository;
import com.att.tdp.issueflow.comment.CommentMapper;
import com.att.tdp.issueflow.comment.CommentRepository;
import com.att.tdp.issueflow.common.exception.BadRequestException;
import com.att.tdp.issueflow.common.exception.ConflictException;
import com.att.tdp.issueflow.common.exception.ForbiddenException;
import com.att.tdp.issueflow.common.exception.NotFoundException;
import com.att.tdp.issueflow.project.dto.CreateProjectRequest;
import com.att.tdp.issueflow.project.dto.ProjectResponse;
import com.att.tdp.issueflow.project.dto.UpdateProjectRequest;
import com.att.tdp.issueflow.security.auth.CurrentUser;
import com.att.tdp.issueflow.ticket.TicketMapper;
import com.att.tdp.issueflow.ticket.TicketDependencyRepository;
import com.att.tdp.issueflow.ticket.TicketRepository;
import com.att.tdp.issueflow.user.Role;
import com.att.tdp.issueflow.user.User;
import com.att.tdp.issueflow.user.UserRepository;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ProjectService {

    private static final String PROJECT_NOT_FOUND = "Project not found";

    private final ProjectRepository projectRepository;
    private final UserRepository userRepository;
    private final TicketRepository ticketRepository;
    private final TicketMapper ticketMapper;
    private final TicketDependencyRepository ticketDependencyRepository;
    private final CommentRepository commentRepository;
    private final CommentMapper commentMapper;
    private final AttachmentRepository attachmentRepository;
    private final ProjectMapper projectMapper;
    private final AuditEventPublisher auditEventPublisher;

    public ProjectService(
            ProjectRepository projectRepository,
            UserRepository userRepository,
            TicketRepository ticketRepository,
            TicketMapper ticketMapper,
            TicketDependencyRepository ticketDependencyRepository,
            CommentRepository commentRepository,
            CommentMapper commentMapper,
            AttachmentRepository attachmentRepository,
            ProjectMapper projectMapper,
            AuditEventPublisher auditEventPublisher
    ) {
        this.projectRepository = projectRepository;
        this.userRepository = userRepository;
        this.ticketRepository = ticketRepository;
        this.ticketMapper = ticketMapper;
        this.ticketDependencyRepository = ticketDependencyRepository;
        this.commentRepository = commentRepository;
        this.commentMapper = commentMapper;
        this.attachmentRepository = attachmentRepository;
        this.projectMapper = projectMapper;
        this.auditEventPublisher = auditEventPublisher;
    }

    @Transactional
    public ProjectResponse create(CreateProjectRequest request) {
        return create(request, null);
    }

    @Transactional
    public ProjectResponse create(CreateProjectRequest request, CurrentUser currentUser) {
        User owner = userRepository.findById(request.ownerId())
                .orElseThrow(() -> new NotFoundException("Project owner not found"));
        String name = request.name().trim();
        String description = projectMapper.trimToNull(request.description());
        if (projectRepository.existsDuplicateActiveProject(name, description, owner.getId())) {
            throw new ConflictException("Duplicate project");
        }

        Project project = projectMapper.toEntity(request, owner, generateProjectKey(request.name()));
        try {
            ProjectResponse response = projectMapper.toResponse(projectRepository.save(project));
            auditEventPublisher.userAction(
                    currentUser == null ? owner.getId() : currentUser.id(),
                    AuditAction.CREATE,
                    AuditableEntityType.PROJECT,
                    response.id(),
                    null,
                    response
            );
            return response;
        } catch (DataIntegrityViolationException exception) {
            throw new ConflictException("Project key already exists", exception);
        }
    }

    @Transactional(readOnly = true)
    public ProjectResponse getById(Long id) {
        return projectMapper.toResponse(findActiveProject(id));
    }

    @Transactional(readOnly = true)
    public List<ProjectResponse> getAll() {
        return projectRepository.findAll().stream()
                .map(projectMapper::toResponse)
                .toList();
    }

    @Transactional
    public ProjectResponse update(Long id, UpdateProjectRequest request) {
        return update(id, request, null);
    }

    @Transactional
    public ProjectResponse update(Long id, UpdateProjectRequest request, CurrentUser currentUser) {
        Project project = findActiveProject(id);
        if (request.name() == null && request.description() == null) {
            throw new BadRequestException("At least one project field must be provided");
        }
        ProjectResponse oldValue = projectMapper.toResponse(project);
        if (request.name() != null) {
            if (request.name().isBlank()) {
                throw new BadRequestException("Project name must not be blank");
            }
            project.setName(request.name().trim());
        }
        if (request.description() != null) {
            if (request.description().isBlank()) {
                throw new BadRequestException("Project description must not be blank");
            }
            project.setDescription(projectMapper.trimToNull(request.description()));
        }
        ProjectResponse response = projectMapper.toResponse(project);
        auditEventPublisher.userAction(
                currentUser == null ? null : currentUser.id(),
                AuditAction.UPDATE,
                AuditableEntityType.PROJECT,
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
        Project project = findActiveProject(id);
        ProjectResponse oldValue = projectMapper.toResponse(project);
        softDeleteProjectChildrenAsSystem(id, currentUser == null ? null : currentUser.id());
        projectRepository.delete(project);
        auditEventPublisher.userAction(
                currentUser == null ? null : currentUser.id(),
                AuditAction.DELETE,
                AuditableEntityType.PROJECT,
                id,
                oldValue,
                null
        );
    }

    @Transactional
    public ProjectResponse restore(Long id, CurrentUser currentUser) {
        requireAdmin(currentUser);
        Project project = projectRepository.findIncludingDeletedById(id)
                .orElseThrow(() -> new NotFoundException(PROJECT_NOT_FOUND));

        if (!project.isDeleted()) {
            return projectMapper.toResponse(project);
        }
        ProjectResponse oldValue = projectMapper.toResponse(project);

        int restored = projectRepository.restoreById(id);
        if (restored == 0) {
            throw new NotFoundException(PROJECT_NOT_FOUND);
        }
        restoreProjectChildrenAsSystem(id, currentUser.id());
        Project restoredProject = projectRepository.findIncludingDeletedById(id)
                .orElseThrow(() -> new NotFoundException(PROJECT_NOT_FOUND));
        ProjectResponse response = projectMapper.toResponse(restoredProject);
        auditEventPublisher.userAction(
                currentUser.id(),
                AuditAction.RESTORE,
                AuditableEntityType.PROJECT,
                id,
                oldValue,
                response
        );
        return response;
    }

    @Transactional(readOnly = true)
    public List<ProjectResponse> getDeletedProjects(CurrentUser currentUser) {
        requireAdmin(currentUser);
        return projectRepository.findDeletedProjects().stream()
                .map(projectMapper::toResponse)
                .toList();
    }

    private Project findActiveProject(Long id) {
        return projectRepository.findById(id)
                .orElseThrow(() -> new NotFoundException(PROJECT_NOT_FOUND));
    }

    private void requireAdmin(CurrentUser currentUser) {
        if (currentUser == null || currentUser.role() != Role.ADMIN) {
            throw new ForbiddenException("Admin role is required");
        }
    }

    private void softDeleteProjectChildrenAsSystem(Long projectId, Long actorId) {
        List<Long> ticketIds = ticketRepository.findActiveIdsByProjectIdIncludingDeletedProject(projectId);
        if (ticketIds.isEmpty()) {
            return;
        }
        List<Long> dependencyIds = ticketDependencyRepository.findActiveIdsTouchingTicketIds(ticketIds);
        List<Long> commentIds = commentRepository.findActiveIdsByTicketIdsIncludingDeletedTickets(ticketIds);
        List<Long> attachmentIds = attachmentRepository.findActiveIdsByTicketIdsIncludingDeletedTickets(ticketIds);
        ticketDependencyRepository.softDeleteActiveTouchingTicketIdsAsSystem(ticketIds);
        commentRepository.softDeleteActiveByTicketIdsAsSystem(ticketIds);
        attachmentRepository.softDeleteActiveByTicketIdsAsSystem(ticketIds);
        ticketRepository.softDeleteActiveByIdsAsSystem(ticketIds);
        dependencyIds.forEach(dependencyId -> auditEventPublisher.systemAction(
                actorId,
                AuditAction.DELETE,
                AuditableEntityType.TICKET_DEPENDENCY,
                dependencyId,
                null,
                null
        ));
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
        ticketIds.forEach(ticketId -> auditEventPublisher.systemAction(
                actorId,
                AuditAction.DELETE,
                AuditableEntityType.TICKET,
                ticketId,
                null,
                null
        ));
    }

    private void restoreProjectChildrenAsSystem(Long projectId, Long actorId) {
        List<Long> ticketIds = ticketRepository.findSystemDeletedIdsByProjectId(projectId);
        if (ticketIds.isEmpty()) {
            return;
        }
        List<Long> dependencyIds = ticketDependencyRepository.findSystemDeletedIdsTouchingTicketIds(ticketIds);
        List<Long> commentIds = commentRepository.findSystemDeletedIdsByTicketIds(ticketIds);
        List<Long> attachmentIds = attachmentRepository.findSystemDeletedIdsByTicketIds(ticketIds);
        ticketRepository.restoreSystemDeletedByProjectId(projectId);
        ticketDependencyRepository.restoreSystemDeletedTouchingTicketIds(ticketIds);
        commentRepository.restoreSystemDeletedByTicketIds(ticketIds);
        attachmentRepository.restoreSystemDeletedByTicketIds(ticketIds);
        ticketIds.forEach(ticketId -> auditEventPublisher.systemAction(
                actorId,
                AuditAction.RESTORE,
                AuditableEntityType.TICKET,
                ticketId,
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
    }

    private String generateProjectKey(String name) {
        String base = name.trim()
                .toUpperCase(Locale.ROOT)
                .replaceAll("[^A-Z0-9]+", "-")
                .replaceAll("(^-|-$)", "");
        if (base.isBlank()) {
            base = "PROJECT";
        }
        if (base.length() > 23) {
            base = base.substring(0, 23).replaceAll("-$", "");
        }
        return base + "-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase(Locale.ROOT);
    }
}
