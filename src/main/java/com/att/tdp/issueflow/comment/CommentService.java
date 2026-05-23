package com.att.tdp.issueflow.comment;

import com.att.tdp.issueflow.audit.AuditAction;
import com.att.tdp.issueflow.audit.AuditEventPublisher;
import com.att.tdp.issueflow.audit.AuditableEntityType;
import com.att.tdp.issueflow.comment.dto.CommentResponse;
import com.att.tdp.issueflow.comment.dto.CreateCommentRequest;
import com.att.tdp.issueflow.comment.dto.MentionedUserResponse;
import com.att.tdp.issueflow.comment.dto.UpdateCommentRequest;
import com.att.tdp.issueflow.common.exception.ConflictException;
import com.att.tdp.issueflow.common.exception.NotFoundException;
import com.att.tdp.issueflow.security.auth.CurrentUser;
import com.att.tdp.issueflow.ticket.Ticket;
import com.att.tdp.issueflow.ticket.TicketRepository;
import com.att.tdp.issueflow.user.User;
import com.att.tdp.issueflow.user.UserRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CommentService {

    private static final String COMMENT_NOT_FOUND = "Comment not found";

    private final CommentRepository commentRepository;
    private final TicketRepository ticketRepository;
    private final UserRepository userRepository;
    private final MentionService mentionService;
    private final CommentMapper commentMapper;
    private final Clock clock;
    private final AuditEventPublisher auditEventPublisher;

    public CommentService(
            CommentRepository commentRepository,
            TicketRepository ticketRepository,
            UserRepository userRepository,
            MentionService mentionService,
            CommentMapper commentMapper,
            Clock clock,
            AuditEventPublisher auditEventPublisher
    ) {
        this.commentRepository = commentRepository;
        this.ticketRepository = ticketRepository;
        this.userRepository = userRepository;
        this.mentionService = mentionService;
        this.commentMapper = commentMapper;
        this.clock = clock;
        this.auditEventPublisher = auditEventPublisher;
    }

    @Transactional
    public CommentResponse create(Long ticketId, CreateCommentRequest request) {
        return create(ticketId, request, null);
    }

    @Transactional
    public CommentResponse create(Long ticketId, CreateCommentRequest request, CurrentUser currentUser) {
        Ticket ticket = ticketRepository.findActiveWithActiveProjectById(ticketId)
                .orElseThrow(() -> new NotFoundException("Ticket not found"));
        User author = userRepository.findById(request.authorId())
                .orElseThrow(() -> new NotFoundException("Comment author not found"));
        String body = request.content().trim();
        if (commentRepository.existsDuplicateActiveComment(ticket.getId(), author.getId(), body)) {
            throw new ConflictException("Duplicate comment");
        }

        Comment comment = commentMapper.toEntity(request, ticket, author);
        MentionService.MentionSyncResult mentionChanges = mentionService.syncMentions(comment);
        Comment savedComment = commentRepository.saveAndFlush(comment);
        Long actorId = currentUser == null ? author.getId() : currentUser.id();
        mentionService.auditMentionChanges(mentionChanges, actorId);
        CommentResponse response = commentMapper.toResponse(savedComment);
        auditEventPublisher.userAction(
                actorId,
                AuditAction.CREATE,
                AuditableEntityType.COMMENT,
                response.id(),
                null,
                response
        );
        return response;
    }

    @Transactional(readOnly = true)
    public List<CommentResponse> getCommentsForTicket(Long ticketId) {
        if (!ticketRepository.existsActiveWithActiveProjectById(ticketId)) {
            throw new NotFoundException("Ticket not found");
        }
        List<Comment> comments = commentRepository.findByTicketIdOrderByCreatedAtAsc(ticketId);
        Map<Long, List<MentionedUserResponse>> activeMentionsByCommentId = activeMentionsByCommentId(comments);
        return comments.stream()
                .map(comment -> commentMapper.toResponse(
                        comment,
                        activeMentionsByCommentId.getOrDefault(comment.getId(), List.of())
                ))
                .toList();
    }

    private Map<Long, List<MentionedUserResponse>> activeMentionsByCommentId(List<Comment> comments) {
        if (comments.isEmpty()) {
            return Map.of();
        }
        List<Long> commentIds = comments.stream()
                .map(Comment::getId)
                .toList();
        return commentRepository.findActiveMentionedUsersByCommentIds(commentIds).stream()
                .collect(Collectors.groupingBy(
                        CommentRepository.ActiveMentionedUserView::getCommentId,
                        Collectors.mapping(
                                mentionedUser -> new MentionedUserResponse(
                                        mentionedUser.getId(),
                                        mentionedUser.getUsername(),
                                        mentionedUser.getFullName()
                                ),
                                Collectors.toList()
                        )
                ));
    }

    @Transactional
    public CommentResponse update(Long commentId, UpdateCommentRequest request) {
        return update(commentId, request, null);
    }

    @Transactional
    public CommentResponse update(Long commentId, UpdateCommentRequest request, CurrentUser currentUser) {
        Comment comment = findCommentForUpdate(commentId);
        return updateComment(comment, request, currentUser);
    }

    @Transactional
    public CommentResponse update(Long ticketId, Long commentId, UpdateCommentRequest request, CurrentUser currentUser) {
        requireActiveTicket(ticketId);
        Comment comment = findCommentForUpdate(commentId);
        validateCommentBelongsToTicket(comment, ticketId);
        return updateComment(comment, request, currentUser);
    }

    private CommentResponse updateComment(Comment comment, UpdateCommentRequest request, CurrentUser currentUser) {
        validateVersion(comment.getVersion(), request.version());
        CommentResponse oldValue = commentMapper.toResponse(comment);
        comment.setBody(request.content().trim());
        comment.setEditedAt(Instant.now(clock));
        MentionService.MentionSyncResult mentionChanges = mentionService.syncMentions(comment);
        Comment savedComment = commentRepository.saveAndFlush(comment);
        Long actorId = currentUser == null ? comment.getAuthor().getId() : currentUser.id();
        mentionService.auditMentionChanges(mentionChanges, actorId);
        CommentResponse response = commentMapper.toResponse(savedComment);
        auditEventPublisher.userAction(
                actorId,
                AuditAction.UPDATE,
                AuditableEntityType.COMMENT,
                comment.getId(),
                oldValue,
                response
        );
        return response;
    }

    @Transactional
    public void delete(Long commentId) {
        delete(commentId, null);
    }

    @Transactional
    public void delete(Long commentId, CurrentUser currentUser) {
        Comment comment = findComment(commentId);
        deleteComment(comment, currentUser);
    }

    @Transactional
    public void delete(Long ticketId, Long commentId, CurrentUser currentUser) {
        requireActiveTicket(ticketId);
        Comment comment = findComment(commentId);
        validateCommentBelongsToTicket(comment, ticketId);
        deleteComment(comment, currentUser);
    }

    private void deleteComment(Comment comment, CurrentUser currentUser) {
        CommentResponse oldValue = commentMapper.toResponse(comment);
        commentRepository.delete(comment);
        auditEventPublisher.userAction(
                currentUser == null ? comment.getAuthor().getId() : currentUser.id(),
                AuditAction.DELETE,
                AuditableEntityType.COMMENT,
                comment.getId(),
                oldValue,
                null
        );
    }

    private Comment findComment(Long commentId) {
        return commentRepository.findById(commentId)
                .orElseThrow(() -> new NotFoundException(COMMENT_NOT_FOUND));
    }

    private Comment findCommentForUpdate(Long commentId) {
        return commentRepository.findByIdForUpdate(commentId)
                .orElseThrow(() -> new NotFoundException(COMMENT_NOT_FOUND));
    }

    private void requireActiveTicket(Long ticketId) {
        if (!ticketRepository.existsActiveWithActiveProjectById(ticketId)) {
            throw new NotFoundException("Ticket not found");
        }
    }

    private void validateCommentBelongsToTicket(Comment comment, Long ticketId) {
        if (comment.getTicket() == null || !comment.getTicket().getId().equals(ticketId)) {
            throw new NotFoundException(COMMENT_NOT_FOUND);
        }
    }

    private void validateVersion(long currentVersion, Long requestedVersion) {
        if (requestedVersion != null && requestedVersion != currentVersion) {
            throw new ConflictException("Comment update conflict");
        }
    }
}
