package com.att.tdp.issueflow.security.auth;

import com.att.tdp.issueflow.user.Role;

public record CurrentUser(
        Long id,
        String username,
        String email,
        String fullName,
        Role role
) {
}
