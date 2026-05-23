package com.att.tdp.issueflow.security.jwt;

import com.att.tdp.issueflow.security.auth.CurrentUser;
import com.att.tdp.issueflow.user.Role;
import com.att.tdp.issueflow.user.User;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import javax.crypto.SecretKey;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class JwtTokenService {

    private static final String USERNAME_CLAIM = "username";
    private static final String EMAIL_CLAIM = "email";
    private static final String FULL_NAME_CLAIM = "fullName";
    private static final String ROLE_CLAIM = "role";

    private final SecretKey secretKey;
    private final Duration expiration;
    private final Clock clock;

    public JwtTokenService(
            @Value("${issueflow.security.jwt.secret}") String secret,
            @Value("${issueflow.security.jwt.expiration}") Duration expiration,
            Clock clock
    ) {
        this.secretKey = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.expiration = expiration;
        this.clock = clock;
    }

    public String generateToken(User user) {
        return generateToken(new CurrentUser(
                user.getId(),
                user.getUsername(),
                user.getEmail(),
                user.getFullName(),
                user.getRole()
        ));
    }

    public String generateToken(CurrentUser currentUser) {
        Instant issuedAt = Instant.now(clock);
        Instant expiresAt = issuedAt.plus(expiration);

        return Jwts.builder()
                .subject(String.valueOf(currentUser.id()))
                .claim(USERNAME_CLAIM, currentUser.username())
                .claim(EMAIL_CLAIM, currentUser.email())
                .claim(FULL_NAME_CLAIM, currentUser.fullName())
                .claim(ROLE_CLAIM, currentUser.role().name())
                .issuedAt(Date.from(issuedAt))
                .expiration(Date.from(expiresAt))
                .signWith(secretKey)
                .compact();
    }

    public boolean isValid(String token) {
        try {
            parseClaims(token);
            return true;
        } catch (JwtException | IllegalArgumentException exception) {
            return false;
        }
    }

    public Long getUserId(String token) {
        return Long.valueOf(parseClaims(token).getSubject());
    }

    public String getUsername(String token) {
        return parseClaims(token).get(USERNAME_CLAIM, String.class);
    }

    public Role getRole(String token) {
        return Role.valueOf(parseClaims(token).get(ROLE_CLAIM, String.class));
    }

    public CurrentUser getCurrentUser(String token) {
        Claims claims = parseClaims(token);
        String username = claims.get(USERNAME_CLAIM, String.class);
        String email = claims.get(EMAIL_CLAIM, String.class);
        String fullName = claims.get(FULL_NAME_CLAIM, String.class);
        return new CurrentUser(
                Long.valueOf(claims.getSubject()),
                username,
                email == null ? "" : email,
                fullName == null ? username : fullName,
                Role.valueOf(claims.get(ROLE_CLAIM, String.class))
        );
    }

    public Instant getExpiresAt(String token) {
        return parseClaims(token).getExpiration().toInstant();
    }

    public long getExpirationSeconds() {
        return expiration.toSeconds();
    }

    private Claims parseClaims(String token) {
        return Jwts.parser()
                .verifyWith(secretKey)
                .clock(() -> Date.from(Instant.now(clock)))
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }
}
