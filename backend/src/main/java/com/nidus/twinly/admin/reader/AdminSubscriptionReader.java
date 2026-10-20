package com.nidus.twinly.admin.reader;

import com.nidus.twinly.admin.domain.SubscriptionKind;
import com.nidus.twinly.admin.dto.result.AdminPageResult;
import com.nidus.twinly.admin.dto.result.AdminSubscriptionResult;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class AdminSubscriptionReader {

    private static final String SELECT = """
            SELECT e.user_id,
                   %s AS kind,
                   (e.expires_at IS NULL OR e.expires_at > UTC_TIMESTAMP(6)) AS active,
                   e.expires_at,
                   e.store,
                   e.synced_at
            FROM user_entitlements e
            JOIN users u ON u.id = e.user_id
            """.formatted(AdminSql.SUBSCRIPTION_KIND);

    private static final String BY_USER_IDS = SELECT + """
            WHERE e.entitlement = 'simulation_access'
              AND e.user_id IN (:userIds)
            """;

    private static final String PAGE_CONDITION = """
            WHERE u.is_seed = FALSE
              AND e.entitlement = 'simulation_access'
              AND (:kind IS NULL OR %s = :kind)
              AND (:activeOnly = FALSE OR e.expires_at IS NULL OR e.expires_at > UTC_TIMESTAMP(6))
            """.formatted(AdminSql.SUBSCRIPTION_KIND);

    private static final String PAGE = SELECT + PAGE_CONDITION + """
            ORDER BY e.synced_at DESC, e.id DESC
            LIMIT :size OFFSET :offset
            """;

    private static final String COUNT = """
            SELECT COUNT(*)
            FROM user_entitlements e
            JOIN users u ON u.id = e.user_id
            """ + PAGE_CONDITION;

    private static final String ACTIVE_COUNTS_BY_KIND = """
            SELECT %s AS kind, COUNT(*) AS cnt
            FROM user_entitlements e
            JOIN users u ON u.id = e.user_id
            WHERE u.is_seed = FALSE AND %s
            GROUP BY kind
            """.formatted(AdminSql.SUBSCRIPTION_KIND, AdminSql.ACTIVE_SIMULATION_ACCESS);

    private static final RowMapper<AdminSubscriptionResult> ROW_MAPPER = (rs, rowNum) -> new AdminSubscriptionResult(
            rs.getLong("user_id"),
            SubscriptionKind.valueOf(rs.getString("kind")),
            rs.getBoolean("active"),
            instant(rs.getTimestamp("expires_at")),
            rs.getString("store"),
            instant(rs.getTimestamp("synced_at"))
    );

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public Map<Long, AdminSubscriptionResult> byUserIds(List<Long> userIds) {
        if (userIds.isEmpty()) {
            return Map.of();
        }

        return jdbcTemplate.query(BY_USER_IDS, Map.of("userIds", userIds), ROW_MAPPER).stream()
                .collect(Collectors.toMap(AdminSubscriptionResult::userId, Function.identity()));
    }

    public AdminPageResult<AdminSubscriptionResult> page(SubscriptionKind kind, boolean activeOnly, int page, int size) {
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("kind", kind == null ? null : kind.name())
                .addValue("activeOnly", activeOnly)
                .addValue("size", size)
                .addValue("offset", page * size);

        List<AdminSubscriptionResult> items = jdbcTemplate.query(PAGE, params, ROW_MAPPER);
        Long total = jdbcTemplate.queryForObject(COUNT, params, Long.class);

        return new AdminPageResult<>(items, page, size, total == null ? 0 : total);
    }

    public Map<SubscriptionKind, Long> activeCountsByKind() {
        return jdbcTemplate.query(ACTIVE_COUNTS_BY_KIND, Map.of(), (rs, rowNum) -> Map.entry(
                        SubscriptionKind.valueOf(rs.getString("kind")), rs.getLong("cnt"))).stream()
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
    }

    private static Instant instant(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toInstant();
    }
}
