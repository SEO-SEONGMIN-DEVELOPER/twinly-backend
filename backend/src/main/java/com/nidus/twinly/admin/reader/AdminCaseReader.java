package com.nidus.twinly.admin.reader;

import com.nidus.twinly.admin.domain.CaseStatusFilter;
import com.nidus.twinly.admin.dto.result.AdminPageResult;
import com.nidus.twinly.common.domain.Gender;
import com.nidus.twinly.common.feedback.FeedbackStatus;
import com.nidus.twinly.common.feedback.FeedbackType;
import com.nidus.twinly.report.domain.ReportReason;
import com.nidus.twinly.report.domain.ReportStatus;
import com.nidus.twinly.report.domain.ViolationKind;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class AdminCaseReader {

    private static final String PENDING_REPORT_COUNTS = """
            SELECT 'USER' AS source, u.gender, COUNT(*) AS cnt
            FROM reports r
            JOIN users u ON u.id = r.user_id
            WHERE r.status IN ('PENDING', 'IN_REVIEW')
            GROUP BY u.gender
            UNION ALL
            SELECT 'AI' AS source, u.gender, COUNT(*) AS cnt
            FROM ai_utterance_reports r
            JOIN users u ON u.id = r.user_id
            WHERE r.status IN ('PENDING', 'IN_REVIEW')
            GROUP BY u.gender
            """;

    private static final String PENDING_SUGGESTION_COUNT = """
            SELECT COUNT(*)
            FROM user_feedbacks f
            WHERE f.type = 'SUGGESTION' AND f.status IN ('RECEIVED', 'IN_REVIEW')
            """;

    private static final String WITHDRAWAL_COUNT = "SELECT COUNT(*) FROM user_feedbacks f WHERE f.type = 'WITHDRAWAL'";

    private static final String WITHDRAWAL_OPTION_COUNTS = """
            SELECT o.option_id, COUNT(*) AS cnt
            FROM user_feedback_options o
            JOIN user_feedbacks f ON f.id = o.feedback_id
            WHERE f.type = 'WITHDRAWAL'
            GROUP BY o.option_id
            """;

    private static final String FEEDBACK_OPTIONS = """
            SELECT o.feedback_id, o.option_id
            FROM user_feedback_options o
            WHERE o.feedback_id IN (:feedbackIds)
            ORDER BY o.feedback_id, o.option_id
            """;

    private static final RowMapper<UserReportRow> USER_REPORT_ROW = (rs, rowNum) -> new UserReportRow(
            rs.getLong("id"),
            rs.getLong("user_id"),
            rs.getLong("reported_user_id"),
            ReportReason.valueOf(rs.getString("reason")),
            rs.getString("detail"),
            ReportStatus.valueOf(rs.getString("status")),
            rs.getString("review_note"),
            rs.getString("action_note"),
            instant(rs.getTimestamp("processed_at")),
            instant(rs.getTimestamp("created_at")));

    private static final RowMapper<AiReportRow> AI_REPORT_ROW = (rs, rowNum) -> new AiReportRow(
            rs.getLong("id"),
            rs.getLong("user_id"),
            rs.getLong("reported_user_id"),
            rs.getLong("scene_id"),
            rs.getString("place"),
            rs.getObject("starts_at", LocalDateTime.class),
            rs.getString("utterance_text"),
            rs.getString("reason"),
            ReportStatus.valueOf(rs.getString("status")),
            rs.getString("violation_kind") == null ? null : ViolationKind.valueOf(rs.getString("violation_kind")),
            rs.getString("review_note"),
            rs.getString("action_note"),
            instant(rs.getTimestamp("processed_at")),
            instant(rs.getTimestamp("created_at")));

    private static final RowMapper<FeedbackRow> FEEDBACK_ROW = (rs, rowNum) -> new FeedbackRow(
            rs.getLong("id"),
            rs.getLong("user_id"),
            rs.getString("detail"),
            rs.getString("app_platform"),
            rs.getString("app_version"),
            FeedbackStatus.valueOf(rs.getString("status")),
            rs.getString("memo"),
            instant(rs.getTimestamp("created_at")));

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public PendingCounts pendingCounts() {
        Map<Gender, Long> reportersByGender = new EnumMap<>(Gender.class);
        Map<String, Long> countBySource = new HashMap<>();

        jdbcTemplate.query(PENDING_REPORT_COUNTS, Map.of(), rs -> {
            long count = rs.getLong("cnt");
            countBySource.merge(rs.getString("source"), count, Long::sum);
            String gender = rs.getString("gender");
            if (gender != null) {
                reportersByGender.merge(Gender.valueOf(gender), count, Long::sum);
            }
        });
        Long suggestions = jdbcTemplate.queryForObject(PENDING_SUGGESTION_COUNT, Map.of(), Long.class);

        return new PendingCounts(
                countBySource.getOrDefault("USER", 0L),
                countBySource.getOrDefault("AI", 0L),
                reportersByGender,
                suggestions == null ? 0 : suggestions);
    }

    public AdminPageResult<UserReportRow> userReports(CaseStatusFilter filter, int page, int size) {
        String where = filter == null ? "" : " WHERE " + filter.reportCondition("r.status");

        return page("""
                SELECT r.id, r.user_id, r.reported_user_id, r.reason, r.detail, r.status,
                       r.review_note, r.action_note, r.processed_at, r.created_at
                FROM reports r""" + where, "r.id", "SELECT COUNT(*) FROM reports r" + where, USER_REPORT_ROW, page, size);
    }

    public AdminPageResult<AiReportRow> aiReports(CaseStatusFilter filter, int page, int size) {
        String where = filter == null ? "" : " WHERE " + filter.reportCondition("r.status");

        return page("""
                SELECT r.id, r.user_id, r.reported_user_id, r.scene_id, s.place, s.starts_at, r.utterance_text, r.reason,
                       r.status, r.violation_kind, r.review_note, r.action_note, r.processed_at, r.created_at
                FROM ai_utterance_reports r
                LEFT JOIN scenes s ON s.id = r.scene_id""" + where, "r.id",
                "SELECT COUNT(*) FROM ai_utterance_reports r" + where, AI_REPORT_ROW, page, size);
    }

    public AdminPageResult<FeedbackRow> feedbacks(FeedbackType type, CaseStatusFilter filter, int page, int size) {
        String where = " WHERE f.type = '" + type.name() + "'" + (filter == null ? "" : " AND " + feedbackCondition(filter));

        return page("""
                SELECT f.id, f.user_id, f.detail, f.app_platform, f.app_version, f.status, f.memo, f.created_at
                FROM user_feedbacks f""" + where, "f.id", "SELECT COUNT(*) FROM user_feedbacks f" + where, FEEDBACK_ROW, page, size);
    }

    public Map<Long, List<Long>> optionIdsByFeedbackId(List<Long> feedbackIds) {
        if (feedbackIds.isEmpty()) {
            return Map.of();
        }

        Map<Long, List<Long>> result = new HashMap<>();
        jdbcTemplate.query(FEEDBACK_OPTIONS, Map.of("feedbackIds", feedbackIds), rs -> {
            result.computeIfAbsent(rs.getLong("feedback_id"), id -> new ArrayList<>()).add(rs.getLong("option_id"));
        });

        return result;
    }

    public long withdrawalCount() {
        Long count = jdbcTemplate.queryForObject(WITHDRAWAL_COUNT, Map.of(), Long.class);

        return count == null ? 0 : count;
    }

    public Map<Long, Long> withdrawalOptionCounts() {
        Map<Long, Long> result = new HashMap<>();
        jdbcTemplate.query(WITHDRAWAL_OPTION_COUNTS, Map.of(), rs -> {
            result.put(rs.getLong("option_id"), rs.getLong("cnt"));
        });

        return result;
    }

    private String feedbackCondition(CaseStatusFilter filter) {
        return switch (filter) {
            case PENDING -> "f.status = 'RECEIVED'";
            case IN_REVIEW -> "f.status = 'IN_REVIEW'";
            case DONE -> "f.status = 'DONE'";
        };
    }

    private <T> AdminPageResult<T> page(String select, String orderColumn, String count, RowMapper<T> rowMapper, int page, int size) {
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("size", size)
                .addValue("offset", page * size);

        List<T> items = jdbcTemplate.query(select + " ORDER BY " + orderColumn + " DESC LIMIT :size OFFSET :offset", params, rowMapper);
        Long total = jdbcTemplate.queryForObject(count, params, Long.class);

        return new AdminPageResult<>(items, page, size, total == null ? 0 : total);
    }

    private static Instant instant(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toInstant();
    }

    public record PendingCounts(long userReports, long aiReports, Map<Gender, Long> reportersByGender, long suggestions) {
    }

    public record UserReportRow(Long id, Long reporterId, Long reportedId, ReportReason reason, String detail, ReportStatus status,
                                String reviewNote, String actionNote, Instant processedAt, Instant createdAt) {
    }

    public record AiReportRow(Long id, Long reporterId, Long reportedId, Long sceneId, String scenePlace, LocalDateTime sceneStartsAt,
                              String utteranceText, String reason, ReportStatus status, ViolationKind violationKind,
                              String reviewNote, String actionNote, Instant processedAt, Instant createdAt) {
    }

    public record FeedbackRow(Long id, Long authorId, String detail, String appPlatform, String appVersion, FeedbackStatus status,
                              String memo, Instant createdAt) {
    }
}
