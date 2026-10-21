package com.nidus.twinly.admin.reader;

import com.nidus.twinly.admin.dto.result.AdminGenderCountResult;
import com.nidus.twinly.admin.dto.result.AdminSurveyResult;
import com.nidus.twinly.common.domain.Gender;
import com.nidus.twinly.common.tendency.TendencyOption;
import com.nidus.twinly.common.tendency.TendencyQuestion;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class AdminSurveyReader {

    private static final String MEMBERS = """
            SELECT u.gender, COUNT(*) AS cnt
            FROM users u
            WHERE u.is_seed = FALSE
            GROUP BY u.gender
            """;

    private static final String TENDENCY_ANSWERS = """
            SELECT a.question_id, a.option_id, u.gender, COUNT(*) AS cnt
            FROM user_tendency_answers a
            JOIN users u ON u.id = a.user_id
            WHERE u.is_seed = FALSE
            GROUP BY a.question_id, a.option_id, u.gender
            """;

    private final NamedParameterJdbcTemplate jdbcTemplate;

    @Transactional(readOnly = true)
    public AdminSurveyResult read(List<TendencyQuestion> questions) {
        Map<Gender, Long> members = new EnumMap<>(Gender.class);
        jdbcTemplate.query(MEMBERS, Map.of(), rs -> {
            Gender gender = gender(rs.getString("gender"));
            if (gender != null) {
                members.put(gender, rs.getLong("cnt"));
            }
        });

        Map<Long, Map<Long, Map<Gender, Long>>> answers = new HashMap<>();
        jdbcTemplate.query(TENDENCY_ANSWERS, Map.of(), rs -> {
            Gender gender = gender(rs.getString("gender"));
            if (gender != null) {
                answers.computeIfAbsent(rs.getLong("question_id"), id -> new HashMap<>())
                        .computeIfAbsent(rs.getLong("option_id"), id -> new EnumMap<>(Gender.class))
                        .put(gender, rs.getLong("cnt"));
            }
        });

        return new AdminSurveyResult(
                AdminGenderCountResult.from(members),
                questions.stream()
                        .map(question -> question(question, answers.getOrDefault(question.id(), Map.of())))
                        .toList()
        );
    }

    private AdminSurveyResult.Question question(TendencyQuestion question, Map<Long, Map<Gender, Long>> answersByOption) {
        Map<Gender, Long> respondents = new EnumMap<>(Gender.class);
        List<AdminSurveyResult.Option> options = new ArrayList<>();

        for (TendencyOption option : question.options()) {
            Map<Gender, Long> chosen = answersByOption.getOrDefault(option.id(), Map.of());
            chosen.forEach((gender, count) -> respondents.merge(gender, count, Long::sum));
            options.add(new AdminSurveyResult.Option(option.id(), option.label(), AdminGenderCountResult.from(chosen)));
        }

        return new AdminSurveyResult.Question(question.id(), question.text(), AdminGenderCountResult.from(respondents), options);
    }

    private Gender gender(String raw) {
        return raw == null ? null : Gender.valueOf(raw);
    }
}
