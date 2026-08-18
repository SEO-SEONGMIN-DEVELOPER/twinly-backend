package com.nidus.twinly.common.parallel;

import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class ParallelStoryRenderer {

    private static final Pattern PLACEHOLDER = Pattern.compile("\\{([AB])(와|는|가)?}");
    private static final Map<String, String> PARTICLE_AFTER_FINAL_CONSONANT = Map.of(
            "와", "과",
            "는", "은",
            "가", "이"
    );
    private static final char HANGUL_FIRST = '가';
    private static final char HANGUL_LAST = '힣';
    private static final int HANGUL_FINAL_CONSONANT_CYCLE = 28;

    public String render(String story, String name, String otherName) {
        Matcher matcher = PLACEHOLDER.matcher(story);
        StringBuilder rendered = new StringBuilder();

        while (matcher.find()) {
            String target = "A".equals(matcher.group(1)) ? name : otherName;
            String replacement = target + particle(target, matcher.group(2));

            matcher.appendReplacement(rendered, Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(rendered);

        return rendered.toString();
    }

    private String particle(String name, String particle) {
        if (particle == null) {
            return "";
        }

        return endsWithFinalConsonant(name) ? PARTICLE_AFTER_FINAL_CONSONANT.get(particle) : particle;
    }

    private boolean endsWithFinalConsonant(String name) {
        if (name.isEmpty()) {
            return false;
        }

        char last = name.charAt(name.length() - 1);
        if (last < HANGUL_FIRST || last > HANGUL_LAST) {
            return false;
        }

        return (last - HANGUL_FIRST) % HANGUL_FINAL_CONSONANT_CYCLE != 0;
    }
}
