package com.nidus.twinly.common.scene;

import com.nidus.twinly.user.entity.User;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.Comparator;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Component
public class SceneNameRenderer {

    private static final Pattern PLACEHOLDER = Pattern.compile("\\{user_(\\d+)}(" + Particle.alternation() + ")?");

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
            String name = name(matcher.group(1), nameByUserId);
            String particle = particle(name, matcher.group(2));
            matcher.appendReplacement(rendered, Matcher.quoteReplacement(name + particle));
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

    private String particle(String name, String written) {
        if (written == null) {
            return "";
        }

        return FinalConsonant.of(name)
                .map(finalConsonant -> Particle.of(written).choose(finalConsonant))
                .orElse(written);
    }

    private Long userId(String rawUserId) {
        try {
            return Long.valueOf(rawUserId);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private enum FinalConsonant {
        NONE, RIEUL, OTHER;

        private static final char HANGUL_FIRST = '가';
        private static final char HANGUL_LAST = '힣';
        private static final int FINAL_COUNT = 28;
        private static final int RIEUL_INDEX = 8;
        private static final String DIGITS_WITH_RIEUL = "178";
        private static final String DIGITS_WITH_OTHER = "036";

        static Optional<FinalConsonant> of(String name) {
            if (name.isEmpty()) {
                return Optional.empty();
            }

            char last = name.charAt(name.length() - 1);

            if (last >= HANGUL_FIRST && last <= HANGUL_LAST) {
                int index = (last - HANGUL_FIRST) % FINAL_COUNT;
                return Optional.of(index == 0 ? NONE : index == RIEUL_INDEX ? RIEUL : OTHER);
            }
            if (last >= '0' && last <= '9') {
                return Optional.of(DIGITS_WITH_RIEUL.indexOf(last) >= 0 ? RIEUL
                        : DIGITS_WITH_OTHER.indexOf(last) >= 0 ? OTHER : NONE);
            }
            if (last >= 'a' && last <= 'z') {
                return Optional.of(last == 'l' ? RIEUL : last == 'm' || last == 'n' ? OTHER : NONE);
            }

            return Optional.empty();
        }
    }

    private enum Particle {
        I_GA("이", "가"),
        EUN_NEUN("은", "는"),
        EUL_REUL("을", "를"),
        GWA_WA("과", "와"),
        A_YA("아", "야"),
        IRANG_RANG("이랑", "랑"),
        IEYO_YEYO("이에요", "예요"),
        IYA_YA("이야", "야"),
        IEOT_YEOT("이었", "였"),
        IRA_RA("이라", "라"),
        IRAN_RAN("이란", "란"),
        INA_NA("이나", "나"),
        IMYEO_MYEO("이며", "며"),
        IGO_GO("이고", "고"),
        IMYEON_MYEON("이면", "면"),
        IJI_JI("이지", "지"),
        IDEUN_DEUN("이든", "든"),
        EURO_RO("으로", "로");

        private static final String BARE_I = "이";
        private static final String BARE_I_PATTERN = "이(?![가-힣])";

        private final String afterFinal;
        private final String afterVowel;

        Particle(String afterFinal, String afterVowel) {
            this.afterFinal = afterFinal;
            this.afterVowel = afterVowel;
        }

        static String alternation() {
            return Arrays.stream(values())
                    .flatMap(particle -> Stream.of(particle.afterFinal, particle.afterVowel))
                    .distinct()
                    .sorted(Comparator.comparingInt(String::length).reversed())
                    .map(form -> form.equals(BARE_I) ? BARE_I_PATTERN : form)
                    .collect(Collectors.joining("|"));
        }

        static Particle of(String written) {
            return Arrays.stream(values())
                    .filter(particle -> particle.afterFinal.equals(written) || particle.afterVowel.equals(written))
                    .findFirst()
                    .orElseThrow();
        }

        String choose(FinalConsonant finalConsonant) {
            if (finalConsonant == FinalConsonant.NONE) {
                return afterVowel;
            }
            if (finalConsonant == FinalConsonant.RIEUL && this == EURO_RO) {
                return afterVowel;
            }

            return afterFinal;
        }
    }
}
