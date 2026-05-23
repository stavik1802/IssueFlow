package com.att.tdp.issueflow.comment;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

@Component
public class MentionParser {

    private static final Pattern MENTION_PATTERN = Pattern.compile("(?<![A-Za-z0-9_])@([A-Za-z0-9][A-Za-z0-9._-]{0,79})");

    public List<String> parseUsernames(String content) {
        if (content == null || content.isBlank()) {
            return List.of();
        }

        Matcher matcher = MENTION_PATTERN.matcher(content);
        Set<String> seen = new LinkedHashSet<>();
        List<String> usernames = new ArrayList<>();
        while (matcher.find()) {
            String username = matcher.group(1);
            String normalized = username.toLowerCase(Locale.ROOT);
            if (seen.add(normalized)) {
                usernames.add(username);
            }
        }
        return usernames;
    }
}
