package com.att.tdp.issueflow.security.jwt;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;

class TokenDenyListServiceTest {

    @Test
    void deniedTokenIsRejectedUntilItExpires() {
        Clock clock = Clock.fixed(Instant.parse("2026-05-19T10:15:30Z"), ZoneOffset.UTC);
        TokenDenyListService service = new TokenDenyListService(clock);

        service.deny("token", Instant.parse("2026-05-19T10:20:30Z"));

        assertThat(service.isDenied("token")).isTrue();
        assertThat(service.isDenied("other-token")).isFalse();
    }

    @Test
    void expiredDeniedTokenIsPurged() {
        Clock clock = Clock.fixed(Instant.parse("2026-05-19T10:15:30Z"), ZoneOffset.UTC);
        TokenDenyListService service = new TokenDenyListService(clock);

        service.deny("token", Instant.parse("2026-05-19T10:15:29Z"));

        assertThat(service.isDenied("token")).isFalse();
    }
}
