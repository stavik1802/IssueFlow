package com.att.tdp.issueflow.user.dto;

import com.att.tdp.issueflow.user.Role;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import java.time.Instant;

@JsonPropertyOrder({"id", "username", "email", "fullName", "role"})
public record UserResponse(
        Long id,
        String username,
        String email,
        String fullName,
        Role role,
        @JsonIgnore
        Instant createdAt,
        @JsonIgnore
        Instant updatedAt
) {
}
