package com.att.tdp.issueflow.comment;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class MentionParserTest {

    private final MentionParser mentionParser = new MentionParser();

    @Test
    void parsesMentionsAndIgnoresDuplicatesCaseInsensitively() {
        assertThat(mentionParser.parseUsernames("Ping @Alice and @bob, then @alice again."))
                .containsExactly("Alice", "bob");
    }

    @Test
    void returnsEmptyListWhenContentHasNoMentions() {
        assertThat(mentionParser.parseUsernames("No mentions here")).isEmpty();
    }
}
