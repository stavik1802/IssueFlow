package com.att.tdp.issueflow.security.auth;

public record LoginResponse(
        String accessToken,
        String tokenType,
        long expiresIn
) {
}
