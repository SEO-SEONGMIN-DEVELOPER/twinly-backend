package com.nidus.twinly.admin.reader;

import com.nidus.twinly.admin.domain.SubscriptionKind;
import com.nidus.twinly.admin.dto.result.AdminDashboardResult;
import com.nidus.twinly.admin.dto.result.AdminGenderCountResult;
import com.nidus.twinly.common.crypto.AesGcmEncryptor;
import com.nidus.twinly.common.domain.Gender;
import com.nidus.twinly.common.time.KstTimes;
import com.nidus.twinly.user.entity.User;
import com.nidus.twinly.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.Function;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class AdminDashboardReader {

    private static final String MEMBER_COUNTS = """
            SELECT u.gender, COUNT(*) AS cnt
            FROM users u
            WHERE %s
            GROUP BY u.gender
            """.formatted(AdminSql.ACTIVE_USER);

    private static final String SUBSCRIBER_COUNTS = """
            SELECT u.gender, %s AS kind, COUNT(*) AS cnt
            FROM user_entitlements e
            JOIN users u ON u.id = e.user_id
            WHERE %s AND %s
            GROUP BY u.gender, kind
            """.formatted(AdminSql.SUBSCRIPTION_KIND, AdminSql.ACTIVE_USER, AdminSql.ACTIVE_SIMULATION_ACCESS);

    private static final String WITHDRAWN_COUNTS = """
            SELECT u.gender, u.deleted_at IS NOT NULL AS completed, COUNT(*) AS cnt
            FROM users u
            WHERE u.is_seed = FALSE
              AND (u.deleted_at IS NOT NULL OR u.withdrawal_requested_at IS NOT NULL)
            GROUP BY u.gender, completed
            """;

    private static final String ORGANIZATION_COUNTS = """
            SELECT MIN(u.id) AS sample_user_id, COUNT(*) AS cnt
            FROM users u
            WHERE %s AND u.organization_hash IS NOT NULL
            GROUP BY u.organization_hash
            ORDER BY cnt DESC, sample_user_id
            """.formatted(AdminSql.ACTIVE_USER);

    private static final String BIRTH_DATES = """
            SELECT u.birth_date,
                   EXISTS (SELECT 1 FROM user_entitlements e WHERE e.user_id = u.id AND %s) AS subscribed
            FROM users u
            WHERE %s
            """.formatted(AdminSql.ACTIVE_SIMULATION_ACCESS, AdminSql.ACTIVE_USER);

    private static final int BIRTH_YEAR_LENGTH = 4;
    private static final int MIN_BIRTH_YEAR = 1900;

    private final NamedParameterJdbcTemplate jdbcTemplate;
    private final UserRepository userRepository;
    private final AesGcmEncryptor aesGcmEncryptor;

    @Transactional(readOnly = true)
    public AdminDashboardResult read() {
        List<GenderKindCount> subscribers = jdbcTemplate.query(SUBSCRIBER_COUNTS, Map.of(), (rs, rowNum) -> new GenderKindCount(
                gender(rs.getString("gender")), rs.getString("kind"), rs.getLong("cnt")));
        List<GenderKindCount> withdrawn = jdbcTemplate.query(WITHDRAWN_COUNTS, Map.of(), (rs, rowNum) -> new GenderKindCount(
                gender(rs.getString("gender")), rs.getBoolean("completed") ? "COMPLETED" : "PENDING", rs.getLong("cnt")));

        BirthYearCounts birthYearCounts = birthYearCounts();

        return new AdminDashboardResult(
                AdminGenderCountResult.from(memberCounts()),
                AdminGenderCountResult.from(byGender(subscribers)),
                sumOfKind(subscribers, SubscriptionKind.PAID.name()),
                sumOfKind(subscribers, SubscriptionKind.TRIAL.name()),
                sumOfKind(subscribers, SubscriptionKind.EARLY_GRANT.name()),
                AdminGenderCountResult.from(byGender(withdrawn)),
                sumOfKind(withdrawn, "COMPLETED"),
                sumOfKind(withdrawn, "PENDING"),
                organizations(),
                birthYearCounts.years(),
                birthYearCounts.unknown()
        );
    }

    private BirthYearCounts birthYearCounts() {
        Map<Integer, long[]> countsByYear = new TreeMap<>(Comparator.reverseOrder());
        long[] unknown = new long[1];

        jdbcTemplate.query(BIRTH_DATES, Map.of(), rs -> {
            Integer year = birthYear(rs.getString("birth_date"));
            if (year == null) {
                unknown[0]++;
                return;
            }
            long[] counts = countsByYear.computeIfAbsent(year, key -> new long[2]);
            counts[0]++;
            if (rs.getBoolean("subscribed")) {
                counts[1]++;
            }
        });

        List<AdminDashboardResult.BirthYear> years = countsByYear.entrySet().stream()
                .map(entry -> new AdminDashboardResult.BirthYear(entry.getKey(), entry.getValue()[0], entry.getValue()[1]))
                .toList();

        return new BirthYearCounts(years, unknown[0]);
    }

    private Integer birthYear(String encryptedBirthDate) {
        if (encryptedBirthDate == null) {
            return null;
        }

        try {
            String birthDate = aesGcmEncryptor.decrypt(encryptedBirthDate);
            int year = Integer.parseInt(birthDate.substring(0, BIRTH_YEAR_LENGTH));

            return year >= MIN_BIRTH_YEAR && year <= KstTimes.today().getYear() ? year : null;
        } catch (Exception e) {
            return null;
        }
    }

    private Map<Gender, Long> memberCounts() {
        Map<Gender, Long> counts = new EnumMap<>(Gender.class);

        jdbcTemplate.query(MEMBER_COUNTS, Map.of(), rs -> {
            Gender gender = gender(rs.getString("gender"));
            if (gender != null) {
                counts.merge(gender, rs.getLong("cnt"), Long::sum);
            }
        });

        return counts;
    }

    private List<AdminDashboardResult.Organization> organizations() {
        List<long[]> rows = jdbcTemplate.query(ORGANIZATION_COUNTS, Map.of(),
                (rs, rowNum) -> new long[]{rs.getLong("sample_user_id"), rs.getLong("cnt")});

        Map<Long, User> userById = userRepository.findAllById(rows.stream().map(row -> row[0]).toList()).stream()
                .collect(Collectors.toMap(User::getId, Function.identity()));

        return rows.stream()
                .filter(row -> userById.containsKey(row[0]))
                .map(row -> new AdminDashboardResult.Organization(userById.get(row[0]).getOrganization(), row[1]))
                .toList();
    }

    private Map<Gender, Long> byGender(List<GenderKindCount> counts) {
        Map<Gender, Long> result = new EnumMap<>(Gender.class);

        counts.stream()
                .filter(count -> count.gender() != null)
                .forEach(count -> result.merge(count.gender(), count.count(), Long::sum));

        return result;
    }

    private long sumOfKind(List<GenderKindCount> counts, String kind) {
        return counts.stream()
                .filter(count -> kind.equals(count.kind()))
                .mapToLong(GenderKindCount::count)
                .sum();
    }

    private Gender gender(String raw) {
        return raw == null ? null : Gender.valueOf(raw);
    }

    private record GenderKindCount(Gender gender, String kind, long count) {
    }

    private record BirthYearCounts(List<AdminDashboardResult.BirthYear> years, long unknown) {
    }
}
