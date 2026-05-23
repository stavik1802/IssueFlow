package com.att.tdp.issueflow.security.auth;

import com.att.tdp.issueflow.audit.AuditAction;
import com.att.tdp.issueflow.audit.AuditEventPublisher;
import com.att.tdp.issueflow.audit.AuditableEntityType;
import com.att.tdp.issueflow.common.exception.BadRequestException;
import com.att.tdp.issueflow.security.jwt.JwtTokenService;
import com.att.tdp.issueflow.security.jwt.TokenDenyListService;
import com.att.tdp.issueflow.user.User;
import com.att.tdp.issueflow.user.UserRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {

    private final UserRepository userRepository;
    private final JwtTokenService jwtTokenService;
    private final TokenDenyListService tokenDenyListService;
    private final AuditEventPublisher auditEventPublisher;
    private final String authPassword;

    public AuthService(
            UserRepository userRepository,
            JwtTokenService jwtTokenService,
            TokenDenyListService tokenDenyListService,
            AuditEventPublisher auditEventPublisher,
            @Value("${issueflow.security.auth.password}") String authPassword
    ) {
        this.userRepository = userRepository;
        this.jwtTokenService = jwtTokenService;
        this.tokenDenyListService = tokenDenyListService;
        this.auditEventPublisher = auditEventPublisher;
        this.authPassword = authPassword;
    }

    @Transactional
    public LoginResponse login(LoginRequest request) {
        String username = requireNonBlank(request.username(), "Username is required");
        String password = requireNonBlank(request.password(), "Password is required");
        if (!authPassword.equals(password)) {
            throw new BadRequestException("Invalid username or password");
        }

        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new BadRequestException("Invalid username or password"));
        String token = jwtTokenService.generateToken(user);
        auditEventPublisher.userAction(
                user.getId(),
                AuditAction.LOGIN,
                AuditableEntityType.AUTH,
                user.getId(),
                null,
                new AuthAuditValue(user.getUsername())
        );
        return new LoginResponse(
                token,
                "Bearer",
                jwtTokenService.getExpirationSeconds()
        );
    }

    public void logout(String authorizationHeader) {
        String token = extractBearerToken(authorizationHeader);
        if (!jwtTokenService.isValid(token)) {
            throw new BadRequestException("Invalid token");
        }
        Long userId = jwtTokenService.getUserId(token);
        tokenDenyListService.deny(token, jwtTokenService.getExpiresAt(token));
        auditEventPublisher.userAction(
                userId,
                AuditAction.LOGOUT,
                AuditableEntityType.AUTH,
                userId,
                null,
                null
        );
    }

    @Transactional(readOnly = true)
    public CurrentUser me(CurrentUser currentUser) {
        if (currentUser == null) {
            throw new BadRequestException("Authenticated user is required");
        }
        return currentUser;
    }

    private String extractBearerToken(String authorizationHeader) {
        if (authorizationHeader == null || !authorizationHeader.startsWith("Bearer ")) {
            throw new BadRequestException("Bearer token is required");
        }
        String token = authorizationHeader.substring(7).trim();
        if (token.isEmpty()) {
            throw new BadRequestException("Bearer token is required");
        }
        return token;
    }

    private String requireNonBlank(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new BadRequestException(message);
        }
        return value.trim();
    }

    private record AuthAuditValue(String username) {
    }
}
