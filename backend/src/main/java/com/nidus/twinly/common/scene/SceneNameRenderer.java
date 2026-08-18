package com.nidus.twinly.common.scene;

import com.nidus.twinly.user.entity.User;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Component
public class SceneNameRenderer {

    private static final Pattern PLACEHOLDER = Pattern.compile("\\{user_(\\d+)}");

    public Set<Long> userIds(String text) {
        if (text == null) {
            return Set.of();
        }

        return PLACEHOLDER.matcher(text).results()
                .map(result -> userId(result.group(1)))
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
    }

    public String render(String text, Map<Long, String> nameByUserId) {
        if (text == null) {
            return null;
        }

        Matcher matcher = PLACEHOLDER.matcher(text);
        StringBuilder rendered = new StringBuilder();

        while (matcher.find()) {
            matcher.appendReplacement(rendered, Matcher.quoteReplacement(name(matcher.group(1), nameByUserId)));
        }
        matcher.appendTail(rendered);

        return rendered.toString();
    }

    public SceneLine render(SceneLine line, Map<Long, String> nameByUserId) {
        return switch (line) {
            case SceneNarrationLine narration -> new SceneNarrationLine(
                    narration.t(),
                    render(narration.text(), nameByUserId),
                    narration.occursAt()
            );
            case SceneBubbleLine bubble -> new SceneBubbleLine(
                    bubble.t(),
                    bubble.userId(),
                    render(bubble.action(), nameByUserId),
                    render(bubble.text(), nameByUserId),
                    bubble.occursAt()
            );
        };
    }

    private String name(String rawUserId, Map<Long, String> nameByUserId) {
        Long userId = userId(rawUserId);
        String name = userId == null ? null : nameByUserId.get(userId);

        return name == null ? User.WITHDRAWN_NAME : name;
    }

    private Long userId(String rawUserId) {
        try {
            return Long.valueOf(rawUserId);
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
