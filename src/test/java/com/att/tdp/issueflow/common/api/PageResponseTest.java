package com.att.tdp.issueflow.common.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

class PageResponseTest {

    @Test
    void createsResponseFromSpringPage() {
        PageImpl<String> page = new PageImpl<>(
                List.of("ISSUE-1", "ISSUE-2"),
                PageRequest.of(1, 2),
                5
        );

        PageResponse<String> response = PageResponse.from(page);

        assertThat(response.content()).containsExactly("ISSUE-1", "ISSUE-2");
        assertThat(response.page()).isEqualTo(1);
        assertThat(response.size()).isEqualTo(2);
        assertThat(response.totalElements()).isEqualTo(5);
        assertThat(response.totalPages()).isEqualTo(3);
        assertThat(response.last()).isFalse();
    }

    @Test
    void contentIsImmutable() {
        PageResponse<String> response = new PageResponse<>(List.of("ISSUE-1"), 0, 1, 1, 1, true);

        assertThatThrownBy(() -> response.content().add("ISSUE-2"))
                .isInstanceOf(UnsupportedOperationException.class);
    }
}
