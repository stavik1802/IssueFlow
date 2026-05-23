package com.att.tdp.issueflow.security.jwt;

import static org.assertj.core.api.Assertions.assertThat;

import com.att.tdp.issueflow.user.Role;
import com.att.tdp.issueflow.user.User;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;

class JwtTokenServiceTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-05-19T10:15:30Z"), ZoneOffset.UTC);
    private static final String SECRET = "test-secret-key-that-is-long-enough-for-hmac-sha256";

    private final JwtTokenService jwtTokenService = new JwtTokenService(SECRET, Duration.ofMinutes(30), CLOCK);

    @Test
    void generatesAndValidatesTokenWithExpectedClaims() {
        String token = jwtTokenService.generateToken(user());

        assertThat(jwtTokenService.isValid(token)).isTrue();
        assertThat(jwtTokenService.getUserId(token)).isEqualTo(42L);
        assertThat(jwtTokenService.getUsername(token)).isEqualTo("admin");
        assertThat(jwtTokenService.getRole(token)).isEqualTo(Role.ADMIN);
        assertThat(jwtTokenService.getExpiresAt(token)).isEqualTo(Instant.parse("2026-05-19T10:45:30Z"));
    }

    @Test
    void rejectsMalformedToken() {
        assertThat(jwtTokenService.isValid("not-a-jwt")).isFalse();
    }

    private static User user() {
        User user = new User();
        user.setId(42L);
        user.setUsername("admin");
        user.setEmail("admin@example.com");
        user.setFullName("Admin User");
        user.setRole(Role.ADMIN);
        return user;
    }
}
