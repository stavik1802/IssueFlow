package com.att.tdp.issueflow.comment.dto;

public record MentionedUserResponse(
        Long id,
        String username,
        String fullName
) {
}
