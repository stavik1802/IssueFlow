package com.att.tdp.issueflow.security.jwt;

import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Service;

@Service
public class TokenDenyListService {

    private final Clock clock;
    private final Map<String, Instant> deniedTokens = new ConcurrentHashMap<>();

    public TokenDenyListService(Clock clock) {
        this.clock = clock;
    }

    public void deny(String token, Instant expiresAt) {
        purgeExpiredTokens();
        deniedTokens.put(token, expiresAt);
    }

    public boolean isDenied(String token) {
        purgeExpiredTokens();
        Instant expiresAt = deniedTokens.get(token);
        return expiresAt != null && expiresAt.isAfter(Instant.now(clock));
    }

    private void purgeExpiredTokens() {
        Instant now = Instant.now(clock);
        deniedTokens.entrySet().removeIf(entry -> !entry.getValue().isAfter(now));
    }
}
