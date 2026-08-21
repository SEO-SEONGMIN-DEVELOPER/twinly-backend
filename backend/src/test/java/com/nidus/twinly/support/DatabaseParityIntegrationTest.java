package com.nidus.twinly.support;

import com.nidus.twinly.season.entity.Season;
import com.nidus.twinly.user.entity.User;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import com.nidus.twinly.season.repository.SeasonRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.Duration;
import java.time.Instant;
import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 테스트 DB가 운영 RDS와 같은 규칙으로 동작하는지 고정한다.
 *
 * <p>여기가 어긋나면 다른 모든 통합 테스트가 조용히 운영과 다른 결과를 검증하게 된다.
 * 결함을 못 잡거나, 멀쩡한 코드를 실패로 판정하거나 둘 중 하나다.
 */
class DatabaseParityIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    JdbcTemplate jdbcTemplate;

    @PersistenceContext
    EntityManager entityManager;

    @Autowired
    SeasonRepository seasonRepository;

    @Test
    @DisplayName("컬럼 비교는 운영 RDS와 같이 대소문자를 구분하지 않는다")
    void collation_matches_production() {
        // 운영 RDS는 utf8mb4_0900_ai_ci 다. cs 로 두면 닉네임 중복 판정이 운영과 달라진다.
        // 리터럴끼리의 비교는 연결 콜레이션을 타므로 반드시 실제 컬럼으로 재야 한다.
        User user = saveUser();
        entityManager.flush();

        Integer matched = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM users WHERE nickname = ?", Integer.class,
                user.getNickname().toUpperCase(Locale.ROOT));

        assertThat(matched).isEqualTo(1);
    }

    @Test
    @DisplayName("앱이 쓴 시각과 DB의 UTC_TIMESTAMP가 같은 기준을 쓴다")
    void connection_time_zone_matches_production() {
        // 앱은 Instant 로 쓰고 일부 네이티브 쿼리는 UTC_TIMESTAMP() 로 쓴다. 연결 시간대가 어긋나면
        // 두 값이 JVM 기본 시간대만큼 벌어져, 만료 판정 같은 비교가 로컬에서만 다르게 동작한다.
        Season season = seasonRepository.save(Season.create(
                Instant.now(), Instant.now().plus(Duration.ofDays(30))));

        Number gapSeconds = jdbcTemplate.queryForObject(
                "SELECT TIMESTAMPDIFF(SECOND, created_at, UTC_TIMESTAMP(6)) FROM seasons WHERE id = ?",
                Number.class, season.getId());

        assertThat(gapSeconds.longValue()).isBetween(-5L, 5L);
    }
}
