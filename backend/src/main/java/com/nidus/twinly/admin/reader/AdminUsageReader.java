package com.nidus.twinly.admin.reader;

import com.nidus.twinly.admin.dto.result.AdminGenderCountResult;
import com.nidus.twinly.admin.dto.result.AdminUsageResult;
import com.nidus.twinly.common.domain.Gender;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.EnumMap;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class AdminUsageReader {

    private static final String ROOMS_WITHOUT_SEED = """
            SELECT p.room_id, SUM(p.entry_agreed_at IS NOT NULL) AS entered_count
            FROM chat_room_participations p
            JOIN users u ON u.id = p.user_id
            GROUP BY p.room_id
            HAVING SUM(u.is_seed) = 0
            """;

    private static final String SUBSCRIBERS = """
            SELECT u.gender, COUNT(*) AS cnt
            FROM users u
            WHERE %s AND %s
            GROUP BY u.gender
            """.formatted(AdminSql.ACTIVE_USER, AdminSql.CURRENT_SEASON_PARTICIPANT);

    private static final String BALANCE_GAME_USERS = """
            SELECT u.gender, COUNT(DISTINCT a.user_id) AS cnt
            FROM balance_game_partner_answers a
            JOIN users u ON u.id = a.user_id
            WHERE %s
            GROUP BY u.gender
            """.formatted(AdminSql.ACTIVE_USER);

    private static final String ROOM_COUNTS = """
            SELECT COUNT(*) AS opened, COALESCE(SUM(r.entered_count >= 2), 0) AS both_entered
            FROM (%s) r
            """.formatted(ROOMS_WITHOUT_SEED);

    private static final String ENTRIES_BY_GENDER = """
            SELECT u.gender, COUNT(*) AS opened, SUM(p.entry_agreed_at IS NOT NULL) AS entered
            FROM chat_room_participations p
            JOIN users u ON u.id = p.user_id
            JOIN (%s) r ON r.room_id = p.room_id
            GROUP BY u.gender
            """.formatted(ROOMS_WITHOUT_SEED);

    private static final String PARTNER_GENDERS = """
            SELECT me.gender AS gender, me.gender = other.gender AS same_gender, COUNT(*) AS cnt
            FROM chat_room_participations p
            JOIN users me ON me.id = p.user_id
            JOIN chat_room_participations q ON q.room_id = p.room_id AND q.user_id <> p.user_id
            JOIN users other ON other.id = q.user_id
            JOIN (%s) r ON r.room_id = p.room_id
            WHERE p.entry_agreed_at IS NOT NULL
            GROUP BY me.gender, same_gender
            """.formatted(ROOMS_WITHOUT_SEED);

    private final NamedParameterJdbcTemplate jdbcTemplate;

    @Transactional(readOnly = true)
    public AdminUsageResult read() {
        Map<Gender, Long> opened = new EnumMap<>(Gender.class);
        Map<Gender, Long> entered = new EnumMap<>(Gender.class);
        jdbcTemplate.query(ENTRIES_BY_GENDER, Map.of(), rs -> {
            Gender gender = gender(rs.getString("gender"));
            if (gender != null) {
                opened.put(gender, rs.getLong("opened"));
                entered.put(gender, rs.getLong("entered"));
            }
        });

        long[] sameAndOppositeOfMale = new long[2];
        long[] sameAndOppositeOfFemale = new long[2];
        jdbcTemplate.query(PARTNER_GENDERS, Map.of(), rs -> {
            Gender gender = gender(rs.getString("gender"));
            long[] target = gender == Gender.MALE ? sameAndOppositeOfMale : gender == Gender.FEMALE ? sameAndOppositeOfFemale : null;
            if (target != null) {
                target[rs.getBoolean("same_gender") ? 0 : 1] += rs.getLong("cnt");
            }
        });

        long[] roomCounts = jdbcTemplate.queryForObject(ROOM_COUNTS, Map.of(),
                (rs, rowNum) -> new long[]{rs.getLong("opened"), rs.getLong("both_entered")});

        return new AdminUsageResult(
                AdminGenderCountResult.from(countByGender(SUBSCRIBERS)),
                AdminGenderCountResult.from(countByGender(BALANCE_GAME_USERS)),
                roomCounts == null ? 0 : roomCounts[0],
                roomCounts == null ? 0 : roomCounts[1],
                AdminGenderCountResult.from(opened),
                AdminGenderCountResult.from(entered),
                new AdminUsageResult.PartnerGender(sameAndOppositeOfMale[0], sameAndOppositeOfMale[1]),
                new AdminUsageResult.PartnerGender(sameAndOppositeOfFemale[0], sameAndOppositeOfFemale[1])
        );
    }

    private Map<Gender, Long> countByGender(String sql) {
        Map<Gender, Long> counts = new EnumMap<>(Gender.class);

        jdbcTemplate.query(sql, Map.of(), rs -> {
            Gender gender = gender(rs.getString("gender"));
            if (gender != null) {
                counts.put(gender, rs.getLong("cnt"));
            }
        });

        return counts;
    }

    private Gender gender(String raw) {
        return raw == null ? null : Gender.valueOf(raw);
    }
}
