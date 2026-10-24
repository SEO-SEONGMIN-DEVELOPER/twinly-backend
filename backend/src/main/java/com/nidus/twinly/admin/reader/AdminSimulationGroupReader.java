package com.nidus.twinly.admin.reader;

import com.nidus.twinly.admin.dto.result.AdminGenderCountResult;
import com.nidus.twinly.common.domain.Gender;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Component
@RequiredArgsConstructor
public class AdminSimulationGroupReader {

    private static final String PARTICIPANT_GENDER_COUNTS = """
            SELECT u.pool_number, u.gender, COUNT(*) AS cnt
            FROM users u
            WHERE u.pool_number IS NOT NULL
              AND u.withdrawal_requested_at IS NULL
              AND u.deleted_at IS NULL
              AND EXISTS (SELECT 1 FROM user_entitlements e WHERE e.user_id = u.id AND %s)
              AND :requiredPolicyCount = (
                  SELECT COUNT(DISTINCT a.policy_id)
                  FROM agreements a
                  WHERE a.user_id = u.id
                    AND a.revoked_at IS NULL
                    AND a.policy_id IN (:requiredPolicyIds)
              )
            GROUP BY u.pool_number, u.gender
            """.formatted(AdminSql.ACTIVE_SIMULATION_ACCESS);

    private static final Long NO_POLICY_ID = -1L;

    private final NamedParameterJdbcTemplate jdbcTemplate;

    @Transactional(readOnly = true)
    public Map<Integer, AdminGenderCountResult> participantGenderCounts(Set<Long> requiredPolicyIds) {
        Map<Integer, Map<Gender, Long>> countsByGroup = new HashMap<>();
        Map<String, Object> params = Map.of(
                "requiredPolicyCount", requiredPolicyIds.size(),
                "requiredPolicyIds", requiredPolicyIds.isEmpty() ? List.of(NO_POLICY_ID) : requiredPolicyIds
        );

        jdbcTemplate.query(PARTICIPANT_GENDER_COUNTS, params, rs -> {
            String gender = rs.getString("gender");
            if (gender != null) {
                countsByGroup.computeIfAbsent(rs.getInt("pool_number"), key -> new EnumMap<>(Gender.class))
                        .put(Gender.valueOf(gender), rs.getLong("cnt"));
            }
        });

        Map<Integer, AdminGenderCountResult> result = new HashMap<>();
        countsByGroup.forEach((groupNumber, counts) -> result.put(groupNumber, AdminGenderCountResult.from(counts)));
        return result;
    }
}
