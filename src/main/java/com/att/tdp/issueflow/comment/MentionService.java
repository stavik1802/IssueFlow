package com.att.tdp.issueflow.comment;

import com.att.tdp.issueflow.audit.AuditAction;
import com.att.tdp.issueflow.audit.AuditEventPublisher;
import com.att.tdp.issueflow.audit.AuditableEntityType;
import com.att.tdp.issueflow.comment.dto.CommentResponse;
import com.att.tdp.issueflow.common.exception.BadRequestException;
import com.att.tdp.issueflow.common.exception.NotFoundException;
import com.att.tdp.issueflow.user.User;
import com.att.tdp.issueflow.user.UserRepository;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class MentionService {

    private final MentionRepository mentionRepository;
    private final UserRepository userRepository;
    private final MentionParser mentionParser;
    private final CommentMapper commentMapper;
    private final AuditEventPublisher auditEventPublisher;

    public MentionService(
            MentionRepository mentionRepository,
            UserRepository userRepository,
            MentionParser mentionParser,
            CommentMapper commentMapper,
            AuditEventPublisher auditEventPublisher
    ) {
        this.mentionRepository = mentionRepository;
        this.userRepository = userRepository;
        this.mentionParser = mentionParser;
        this.commentMapper = commentMapper;
        this.auditEventPublisher = auditEventPublisher;
    }

    public MentionSyncResult syncMentions(Comment comment) {
        Set<String> requestedUsernames = normalizedMentionUsernames(comment.getBody());
        Map<String, Mention> existingMentions = new LinkedHashMap<>();
        for (Mention mention : comment.getMentions()) {
            User mentionedUser = mention.getMentionedUser();
            if (mentionedUser != null) {
                existingMentions.put(normalize(mentionedUser.getUsername()), mention);
            }
        }

        Map<String, User> usersByUsername = findMentionedUsers(requestedUsernames);
        if (usersByUsername.size() != requestedUsernames.size()) {
            Set<String> unknownUsernames = new HashSet<>(requestedUsernames);
            unknownUsernames.removeAll(usersByUsername.keySet());
            throw new BadRequestException("Unknown mentioned user: " + unknownUsernames.stream().sorted().findFirst().orElse(""));
        }

        List<RemovedMentionAuditValue> removedMentions = new ArrayList<>();
        comment.getMentions().removeIf(mention -> {
            User mentionedUser = mention.getMentionedUser();
            if (mentionedUser == null || !requestedUsernames.contains(normalize(mentionedUser.getUsername()))) {
                removedMentions.add(RemovedMentionAuditValue.from(mention));
                return true;
            }
            return false;
        });

        List<Mention> addedMentions = new ArrayList<>();
        for (Map.Entry<String, User> entry : usersByUsername.entrySet()) {
            if (!existingMentions.containsKey(entry.getKey())) {
                Mention mention = new Mention();
                mention.setComment(comment);
                mention.setTicket(comment.getTicket());
                mention.setMentionedUser(entry.getValue());
                comment.getMentions().add(mention);
                addedMentions.add(mention);
            }
        }
        return new MentionSyncResult(addedMentions, removedMentions);
    }

    public void auditMentionChanges(MentionSyncResult result, Long actorId) {
        result.added().forEach(mention -> auditEventPublisher.userAction(
                actorId,
                AuditAction.CREATE,
                AuditableEntityType.MENTION,
                mention.getId(),
                null,
                MentionAuditValue.from(mention)
        ));
        result.removed().forEach(mention -> auditEventPublisher.userAction(
                actorId,
                AuditAction.DELETE,
                AuditableEntityType.MENTION,
                mention.id(),
                mention,
                null
        ));
    }

    @Transactional(readOnly = true)
    public List<CommentResponse> getCommentsMentioningUser(Long userId) {
        if (!userRepository.existsById(userId)) {
            throw new NotFoundException("User not found");
        }
        return mentionRepository.findActiveByMentionedUserIdOrderByCommentCreatedAtDesc(userId).stream()
                .map(Mention::getComment)
                .distinct()
                .sorted(Comparator.comparing(Comment::getCreatedAt, Comparator.nullsLast(Comparator.reverseOrder())))
                .map(commentMapper::toResponse)
                .toList();
    }

    private Set<String> normalizedMentionUsernames(String content) {
        Set<String> usernames = new HashSet<>();
        for (String username : mentionParser.parseUsernames(content)) {
            usernames.add(normalize(username));
        }
        return usernames;
    }

    private Map<String, User> findMentionedUsers(Set<String> requestedUsernames) {
        Map<String, User> usersByUsername = new LinkedHashMap<>();
        if (requestedUsernames.isEmpty()) {
            return usersByUsername;
        }
        for (User user : userRepository.findAll()) {
            String normalizedUsername = normalize(user.getUsername());
            if (requestedUsernames.contains(normalizedUsername)) {
                usersByUsername.put(normalizedUsername, user);
            }
        }
        return usersByUsername;
    }

    private String normalize(String username) {
        return username.toLowerCase(Locale.ROOT);
    }

    public record MentionSyncResult(List<Mention> added, List<RemovedMentionAuditValue> removed) {
    }

    private record MentionAuditValue(Long ticketId, Long commentId, Long mentionedUserId, String username) {
        static MentionAuditValue from(Mention mention) {
            return new MentionAuditValue(
                    mention.getTicket() == null ? null : mention.getTicket().getId(),
                    mention.getComment() == null ? null : mention.getComment().getId(),
                    mention.getMentionedUser() == null ? null : mention.getMentionedUser().getId(),
                    mention.getMentionedUser() == null ? null : mention.getMentionedUser().getUsername()
            );
        }
    }

    public record RemovedMentionAuditValue(Long id, Long ticketId, Long commentId, Long mentionedUserId, String username) {
        static RemovedMentionAuditValue from(Mention mention) {
            User mentionedUser = mention.getMentionedUser();
            return new RemovedMentionAuditValue(
                    mention.getId(),
                    mention.getTicket() == null ? null : mention.getTicket().getId(),
                    mention.getComment() == null ? null : mention.getComment().getId(),
                    mentionedUser == null ? null : mentionedUser.getId(),
                    mentionedUser == null ? null : mentionedUser.getUsername()
            );
        }
    }
}
