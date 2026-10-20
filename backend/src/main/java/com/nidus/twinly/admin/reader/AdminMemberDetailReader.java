package com.nidus.twinly.admin.reader;

import com.nidus.twinly.admin.dto.result.AdminMemberDetailResult.RelatedCase;
import com.nidus.twinly.common.domain.VerificationType;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class AdminMemberDetailReader {

    private static final int SCENE_DATE_LIMIT = 60;
    private static final int RELATED_CASE_LIMIT = 30;

    private static final String VERIFIED_AT_BY_TYPE = """
            SELECT v.type, MAX(v.verified_at) AS verified_at
            FROM verifications v
            WHERE v.user_id = :userId
            GROUP BY v.type
            """;

    private static final String SCENE_DATES = """
            SELECT DISTINCT s.date
            FROM scenes s
            WHERE s.user_id = :userId
            ORDER BY s.date DESC
            LIMIT :limit
            """;

    private static final String USER_REPORTS = """
            SELECT r.id, r.user_id, r.reason, r.status, r.created_at
            FROM reports r
            WHERE r.user_id = :userId OR r.reported_user_id = :userId
            ORDER BY r.id DESC
            LIMIT :limit
            """;

    private static final String AI_REPORTS = """
            SELECT r.id, r.user_id, r.utterance_text, r.status, r.created_at
            FROM ai_utterance_reports r
            WHERE r.user_id = :userId OR r.reported_user_id = :userId
            ORDER BY r.id DESC
            LIMIT :limit
            """;

    private static final String SUGGESTIONS = """
            SELECT f.id, f.detail, f.status, f.created_at
            FROM user_feedbacks f
            WHERE f.user_id = :userId AND f.type = 'SUGGESTION'
            ORDER BY f.id DESC
            LIMIT :limit
            """;

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public Map<VerificationType, Instant> verifiedAtByType(Long userId) {
        Map<VerificationType, Instant> result = new EnumMap<>(VerificationType.class);

        jdbcTemplate.query(VERIFIED_AT_BY_TYPE, Map.of("userId", userId), rs -> {
            Instant verifiedAt = instant(rs.getTimestamp("verified_at"));
            if (verifiedAt != null) {
                result.put(VerificationType.valueOf(rs.getString("type")), verifiedAt);
            }
        });

        return result;
    }

    public List<LocalDate> sceneDates(Long userId) {
        return jdbcTemplate.queryForList(SCENE_DATES, Map.of("userId", userId, "limit", SCENE_DATE_LIMIT), LocalDate.class);
    }

    public List<RelatedCase> relatedCases(Long userId) {
        Map<String, Object> params = Map.of("userId", userId, "limit", RELATED_CASE_LIMIT);
        List<RelatedCase> cases = new ArrayList<>();

        cases.addAll(jdbcTemplate.query(USER_REPORTS, params, (rs, rowNum) -> new RelatedCase(
                "USER_REPORT",
                rs.getLong("id"),
                rs.getLong("user_id") == userId ? "REPORTER" : "REPORTED",
                rs.getString("reason"),
                rs.getString("status"),
                instant(rs.getTimestamp("created_at")))));
        cases.addAll(jdbcTemplate.query(AI_REPORTS, params, (rs, rowNum) -> new RelatedCase(
                "AI_UTTERANCE_REPORT",
                rs.getLong("id"),
                rs.getLong("user_id") == userId ? "REPORTER" : "REPORTED",
                rs.getString("utterance_text"),
                rs.getString("status"),
                instant(rs.getTimestamp("created_at")))));
        cases.addAll(jdbcTemplate.query(SUGGESTIONS, params, (rs, rowNum) -> new RelatedCase(
                "SUGGESTION",
                rs.getLong("id"),
                "AUTHOR",
                rs.getString("detail"),
                rs.getString("status"),
                instant(rs.getTimestamp("created_at")))));

        cases.sort(Comparator.comparing(RelatedCase::createdAt, Comparator.nullsLast(Comparator.reverseOrder())));
        return cases;
    }

    private static Instant instant(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toInstant();
    }
}
