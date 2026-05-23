package com.att.tdp.issueflow.security.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;

import com.att.tdp.issueflow.audit.AuditAction;
import com.att.tdp.issueflow.audit.AuditEventPublisher;
import com.att.tdp.issueflow.audit.AuditableEntityType;
import com.att.tdp.issueflow.common.exception.BadRequestException;
import com.att.tdp.issueflow.security.jwt.JwtTokenService;
import com.att.tdp.issueflow.security.jwt.TokenDenyListService;
import com.att.tdp.issueflow.user.Role;
import com.att.tdp.issueflow.user.User;
import com.att.tdp.issueflow.user.UserRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-05-19T10:15:30Z"), ZoneOffset.UTC);
    private static final String SECRET = "test-secret-key-that-is-long-enough-for-hmac-sha256";

    @Mock
    private AuditEventPublisher auditEventPublisher;

    @Mock
    private UserRepository userRepository;

    private AuthService authService;
    private TokenDenyListService tokenDenyListService;

    @BeforeEach
    void setUp() {
        JwtTokenService jwtTokenService = new JwtTokenService(SECRET, Duration.ofHours(1), CLOCK);
        tokenDenyListService = new TokenDenyListService(CLOCK);
        authService = new AuthService(userRepository, jwtTokenService, tokenDenyListService, auditEventPublisher, "secret");
    }

    @Test
    void loginSucceedsForExistingUserWithConfiguredPassword() {
        User user = user("jdoe", Role.DEVELOPER);
        org.mockito.Mockito.when(userRepository.findByUsername("jdoe")).thenReturn(Optional.of(user));

        LoginResponse response = authService.login(new LoginRequest("jdoe", "secret"));

        assertThat(response.accessToken()).isNotBlank();
        assertThat(response.tokenType()).isEqualTo("Bearer");
        assertThat(response.expiresIn()).isEqualTo(3600);
        verify(auditEventPublisher).userAction(
                eq(1L),
                eq(AuditAction.LOGIN),
                eq(AuditableEntityType.AUTH),
                eq(1L),
                eq(null),
                any()
        );
    }

    @Test
    void loginFailsWhenUserDoesNotExist() {
        org.mockito.Mockito.when(userRepository.findByUsername("missing")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.login(new LoginRequest("missing", "secret")))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("Invalid username or password");
    }

    @Test
    void loginFailsWithWrongPassword() {
        assertThatThrownBy(() -> authService.login(new LoginRequest("jdoe", "wrong")))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("Invalid username or password");
    }

    @Test
    void loginFailsWithBlankUsername() {
        assertThatThrownBy(() -> authService.login(new LoginRequest(" ", "password")))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("Username is required");
    }

    @Test
    void loginFailsWithBlankPassword() {
        assertThatThrownBy(() -> authService.login(new LoginRequest("jdoe", " ")))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("Password is required");
    }

    @Test
    void logoutAddsTokenToDenyList() {
        User user = user("jdoe", Role.DEVELOPER);
        org.mockito.Mockito.when(userRepository.findByUsername("jdoe")).thenReturn(Optional.of(user));
        LoginResponse response = authService.login(new LoginRequest("jdoe", "secret"));

        authService.logout("Bearer " + response.accessToken());

        assertThat(tokenDenyListService.isDenied(response.accessToken())).isTrue();
        verify(auditEventPublisher).userAction(
                eq(1L),
                eq(AuditAction.LOGOUT),
                eq(AuditableEntityType.AUTH),
                eq(1L),
                eq(null),
                eq(null)
        );
    }

    @Test
    void meReturnsCurrentAuthenticatedPrincipal() {
        CurrentUser principal = new CurrentUser(1L, "jdoe", "jdoe@example.com", "John Doe", Role.ADMIN);

        CurrentUser response = authService.me(principal);

        assertThat(response)
                .extracting(CurrentUser::id, CurrentUser::username, CurrentUser::email, CurrentUser::fullName, CurrentUser::role)
                .containsExactly(1L, "jdoe", "jdoe@example.com", "John Doe", Role.ADMIN);
    }

    private User user(String username, Role role) {
        User user = new User();
        user.setId(1L);
        user.setUsername(username);
        user.setEmail(username + "@example.com");
        user.setFullName("John Doe");
        user.setRole(role);
        user.setActive(true);
        return user;
    }
}
