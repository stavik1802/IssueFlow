package com.att.tdp.issueflow.comment.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import java.time.Instant;
import java.util.List;

@JsonPropertyOrder({"id", "ticketId", "authorId", "content", "mentionedUsers"})
public record CommentResponse(
        Long id,
        Long ticketId,
        Long authorId,
        @JsonIgnore
        String authorUsername,
        String content,
        List<MentionedUserResponse> mentionedUsers,
        @JsonIgnore
        Instant createdAt,
        @JsonIgnore
        Instant updatedAt,
        @JsonIgnore
        Instant editedAt,
        @JsonIgnore
        long version
) {
}
