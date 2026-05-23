package com.att.tdp.issueflow.comment;

import com.att.tdp.issueflow.comment.dto.CommentResponse;
import com.att.tdp.issueflow.comment.dto.CreateCommentRequest;
import com.att.tdp.issueflow.comment.dto.MentionedUserResponse;
import com.att.tdp.issueflow.ticket.Ticket;
import com.att.tdp.issueflow.user.User;
import java.util.Comparator;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class CommentMapper {

    public Comment toEntity(CreateCommentRequest request, Ticket ticket, User author) {
        Comment comment = new Comment();
        comment.setTicket(ticket);
        comment.setAuthor(author);
        comment.setBody(request.content().trim());
        return comment;
    }

    public CommentResponse toResponse(Comment comment) {
        List<MentionedUserResponse> mentionedUsers = comment.getMentions().stream()
                .map(Mention::getMentionedUser)
                .sorted(Comparator.comparing(User::getUsername, String.CASE_INSENSITIVE_ORDER))
                .map(user -> new MentionedUserResponse(
                        user.getId(),
                        user.getUsername(),
                        user.getFullName()
                ))
                .toList();
        return toResponse(comment, mentionedUsers);
    }

    public CommentResponse toResponse(Comment comment, List<MentionedUserResponse> mentionedUsers) {
        return new CommentResponse(
                comment.getId(),
                comment.getTicket().getId(),
                comment.getAuthor().getId(),
                comment.getAuthor().getUsername(),
                comment.getBody(),
                mentionedUsers,
                comment.getCreatedAt(),
                comment.getUpdatedAt(),
                comment.getEditedAt(),
                comment.getVersion()
        );
    }
}
