package com.nidus.twinly.people.integration;

import com.jayway.jsonpath.JsonPath;
import com.nidus.twinly.block.entity.Block;
import com.nidus.twinly.block.repository.BlockRepository;
import com.nidus.twinly.common.domain.Gender;
import com.nidus.twinly.common.photo.PhotoType;
import com.nidus.twinly.legal.entity.Agreement;
import com.nidus.twinly.legal.repository.PolicyRepository.PolicySummary;
import com.nidus.twinly.legal.service.PolicyCatalog.PolicyKey;
import com.nidus.twinly.relationship.entity.Relationship;
import com.nidus.twinly.relationship.repository.RelationshipRepository;
import com.nidus.twinly.season.entity.Season;
import com.nidus.twinly.season.entity.SeasonParticipation;
import com.nidus.twinly.season.repository.SeasonParticipationRepository;
import com.nidus.twinly.season.repository.SeasonRepository;
import com.nidus.twinly.support.AbstractIntegrationTest;
import com.nidus.twinly.user.entity.Photo;
import com.nidus.twinly.user.entity.User;
import com.nidus.twinly.user.repository.PhotoRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.util.ReflectionTestUtils;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.matchesPattern;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.startsWith;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willAnswer;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class PeoplePreviewIntegrationTest extends AbstractIntegrationTest {

    private static final int POOL = 12;
    private static final String DISCLOSURE = "thirdPartyRealIdentityDisclosure";
    private static final Instant YESTERDAY = Instant.now().minus(Duration.ofDays(1)).minus(Duration.ofHours(1));

    @Autowired
    SeasonRepository seasonRepository;

    @Autowired
    SeasonParticipationRepository seasonParticipationRepository;

    @Autowired
    RelationshipRepository relationshipRepository;

    @Autowired
    BlockRepository blockRepository;

    @Autowired
    PhotoRepository photoRepository;

    @Autowired
    EntityManager entityManager;

    Season season;

    @BeforeEach
    void setUp() {
        season = seasonRepository.save(Season.create(Instant.now().minus(Duration.ofDays(7)), Instant.now().plus(Duration.ofDays(7))));
    }

    @Test
    @DisplayName("미리보기 조회: 어제까지 참여한 사람만 고르고 본인·인연·양방향 차단·탈퇴·시드·오늘 참여·비참여자·실신원 동의 철회자·미동의자는 뺀다")
    void previews_excludes_ineligible_people() throws Exception {
        // given: 나와 조건을 통과할 후보 1명, 그리고 각 제외 조건에 하나씩 걸리는 사람들
        User me = participant(Gender.MALE, POOL, YESTERDAY);
        User eligible = participant(Gender.FEMALE, POOL, YESTERDAY);

        participant(Gender.FEMALE, POOL, Instant.now());
        poolUser(Gender.FEMALE, POOL);

        User withdrawn = participant(Gender.FEMALE, POOL, YESTERDAY);
        withdrawn.requestWithdrawal(Duration.ofDays(14));
        userRepository.saveAndFlush(withdrawn);

        User seed = participant(Gender.FEMALE, POOL, YESTERDAY);
        userRepository.markSeed(List.of(seed.getId()));

        User blockedByMe = participant(Gender.FEMALE, POOL, YESTERDAY);
        blockRepository.save(Block.create(me.getId(), blockedByMe.getId()));
        User blockingMe = participant(Gender.FEMALE, POOL, YESTERDAY);
        blockRepository.save(Block.create(blockingMe.getId(), me.getId()));

        User partner = participant(Gender.FEMALE, POOL, YESTERDAY);
        relationshipRepository.save(Relationship.create(me.getId(), LocalDate.now(), "v1", partner.getId(), 0, "{}",
                LocalDateTime.now().plusDays(1), Instant.now()));

        User revoked = participant(Gender.FEMALE, POOL, YESTERDAY);
        revokeDisclosure(revoked.getId());
        participate(poolUser(Gender.FEMALE, POOL), YESTERDAY);

        // when: 나의 실제 액세스 토큰으로 미리보기 조회
        var result = mockMvc.perform(get("/api/v1/people/previews")
                .header("Authorization", bearer(me.getId())));

        // then: 조건을 모두 통과한 1명만, 트윈 프로필 v2 형식으로 내려가고 식별 값은 없다
        result.andExpect(status().isOk())
                .andExpect(jsonPath("$.previews.length()").value(1))
                .andExpect(jsonPath("$.previews[0].ref").value(matchesPattern("[0-9a-f]{12}")))
                .andExpect(jsonPath("$.previews[0].gender").value("female"))
                .andExpect(jsonPath("$.previews[0].organization").value(eligible.shortOrganization()))
                .andExpect(jsonPath("$.previews[0].birthYear").value("00"))
                .andExpect(jsonPath("$.previews[0].profilePhoto").isEmpty())
                .andExpect(jsonPath("$.previews[0].userId").doesNotExist())
                .andExpect(jsonPath("$.previews[0].userName").doesNotExist());
    }

    @Test
    @DisplayName("미리보기 조회: 풀이 다르거나 배정되지 않은 사람도 후보에 넣는다")
    void previews_includes_all_pools() throws Exception {
        // given: 나는 12번 풀, 후보는 13번 풀 1명과 풀 미배정 1명
        User me = participant(Gender.MALE, POOL, YESTERDAY);
        participant(Gender.FEMALE, POOL + 1, YESTERDAY);
        participant(Gender.FEMALE, null, YESTERDAY);

        // when: 미리보기 조회
        var result = mockMvc.perform(get("/api/v1/people/previews")
                .header("Authorization", bearer(me.getId())));

        // then: 풀과 무관하게 두 명 모두 내려간다
        result.andExpect(status().isOk())
                .andExpect(jsonPath("$.previews.length()").value(2));
    }

    @Test
    @DisplayName("미리보기 조회: 현재 시즌 미참여자도 부를 수 있고, 참여자 중에서 양방향 차단을 빼고 고른다")
    void previews_non_participant_gets_participants() throws Exception {
        // given: 나는 비참여, 후보가 될 참여자 1명, 비참여자 1명, 나와 서로 차단한 참여자 2명
        User me = poolUser(Gender.MALE, POOL);
        User eligible = participant(Gender.FEMALE, POOL, YESTERDAY);
        poolUser(Gender.FEMALE, POOL);

        User blockedByMe = participant(Gender.FEMALE, POOL, YESTERDAY);
        blockRepository.save(Block.create(me.getId(), blockedByMe.getId()));
        User blockingMe = participant(Gender.FEMALE, POOL, YESTERDAY);
        blockRepository.save(Block.create(blockingMe.getId(), me.getId()));

        // when: 비참여자의 토큰으로 미리보기 조회
        var result = mockMvc.perform(get("/api/v1/people/previews")
                .header("Authorization", bearer(me.getId())));

        // then: 오류 없이 200, 조건을 통과한 참여자 1명만 내려간다
        result.andExpect(status().isOk())
                .andExpect(jsonPath("$.previews.length()").value(1))
                .andExpect(jsonPath("$.previews[0].organization").value(eligible.shortOrganization()));
    }

    @Test
    @DisplayName("미리보기 조회: 사진은 원본과 연결되지 않는 preview/ 경로로 처음 한 번만 만들고 이후에는 재사용한다")
    void previews_creates_preview_photo_once() throws Exception {
        // given: 사진이 있는 후보 1명, S3 원본은 실제 JPEG 로 내려받아진다
        User me = participant(Gender.MALE, POOL, YESTERDAY);
        User candidate = participant(Gender.FEMALE, POOL, YESTERDAY);
        String sourceKey = "profile/" + candidate.getId() + "/original";
        photoRepository.saveAndFlush(Photo.create(candidate.getId(), PhotoType.PROFILE, sourceKey, 0, 0, 100, 100, Instant.now()));

        given(s3Service.contentLength(sourceKey)).willReturn(Optional.of(1024L));
        willAnswer(invocation -> {
            writeJpeg(invocation.getArgument(1));
            return null;
        }).given(s3Service).download(eq(sourceKey), any());

        // when: 첫 호출
        var first = mockMvc.perform(get("/api/v1/people/previews")
                .header("Authorization", bearer(me.getId())));

        // then: preview/ 경로 서명 URL, 원본 key·사용자 ID 경로는 드러나지 않는다
        first.andExpect(status().isOk())
                .andExpect(jsonPath("$.previews[0].profilePhoto").value(startsWith("https://test.cloudfront.net/preview/")))
                .andExpect(jsonPath("$.previews[0].profilePhoto").value(not(containsString("profile/"))));
        String firstUrl = JsonPath.read(first.andReturn().getResponse().getContentAsString(), "$.previews[0].profilePhoto");

        // then: DB 에 원본 key 는 그대로, preview_key 가 응답 URL 의 경로로 저장됐다
        entityManager.clear();
        Photo saved = photoRepository.findByUserIdAndType(candidate.getId(), PhotoType.PROFILE).orElseThrow();
        assertThat(saved.getKey()).isEqualTo(sourceKey);
        assertThat(saved.getPreviewKey()).startsWith("preview/");
        assertThat(pathOf(firstUrl)).isEqualTo("https://test.cloudfront.net/" + saved.getPreviewKey());

        // when: 다음 요청(새 영속성 컨텍스트)에서 다시 호출
        var second = mockMvc.perform(get("/api/v1/people/previews")
                .header("Authorization", bearer(me.getId())));

        // then: 같은 미리보기 key 를 재사용하고, 원본은 한 번만 내려받았다
        String secondUrl = JsonPath.read(second.andReturn().getResponse().getContentAsString(), "$.previews[0].profilePhoto");
        assertThat(pathOf(secondUrl)).isEqualTo(pathOf(firstUrl));
        verify(s3Service, times(1)).download(eq(sourceKey), any());
    }

    @Test
    @DisplayName("미리보기 조회: 후보가 사진을 바꾸면 저장된 미리보기가 비워지고 다음 조회 때 새 사진으로 다시 만든다")
    void previews_regenerates_after_photo_change() throws Exception {
        // given: 이미 미리보기가 만들어진 사진을 가진 후보가 새 원본으로 사진을 바꿨다
        User me = participant(Gender.MALE, POOL, YESTERDAY);
        User candidate = participant(Gender.FEMALE, POOL, YESTERDAY);
        Photo photo = Photo.create(candidate.getId(), PhotoType.PROFILE, "profile/" + candidate.getId() + "/old", 0, 0, 100, 100, Instant.now());
        photo.changeThumbnailKey("profile/" + candidate.getId() + "/old-thumb");
        ReflectionTestUtils.setField(photo, "previewKey", "preview/old");
        photoRepository.saveAndFlush(photo);

        String newKey = "profile/" + candidate.getId() + "/new";
        photo.changePhoto(newKey, 0, 0, 100, 100);
        photoRepository.saveAndFlush(photo);
        entityManager.clear();

        given(s3Service.contentLength(newKey)).willReturn(Optional.of(1024L));
        willAnswer(invocation -> {
            writeJpeg(invocation.getArgument(1));
            return null;
        }).given(s3Service).download(eq(newKey), any());

        // when: 미리보기 조회
        var result = mockMvc.perform(get("/api/v1/people/previews")
                .header("Authorization", bearer(me.getId())));

        // then: 옛 미리보기가 아닌 새 미리보기 URL 이 내려가고, DB 에도 새 key 가 저장됐다
        result.andExpect(status().isOk())
                .andExpect(jsonPath("$.previews[0].profilePhoto").value(startsWith("https://test.cloudfront.net/preview/")))
                .andExpect(jsonPath("$.previews[0].profilePhoto").value(not(containsString("preview/old"))));
        entityManager.clear();
        Photo saved = photoRepository.findByUserIdAndType(candidate.getId(), PhotoType.PROFILE).orElseThrow();
        assertThat(saved.getPreviewKey()).startsWith("preview/").isNotEqualTo("preview/old");
    }

    @Test
    @DisplayName("미리보기 조회: 인증 없이 호출하면 401 을 반환한다")
    void previews_without_auth_returns_401() throws Exception {
        // when: Authorization 헤더 없이 미리보기 조회
        var result = mockMvc.perform(get("/api/v1/people/previews"));

        // then: 실제 시큐리티 필터가 막아 401
        result.andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("미리보기 v2 조회: 참여자에게 1.2 이상 동의자는 key 없는 원본 사진·닉네임을, 1.1 까지만 동의한 사람은 가린 사진만 준다")
    void previewsV2_participant_sees_unmasked_only_for_agreed_people() throws Exception {
        // given: 참여자인 나, 1.2 에도 동의한 후보와 1.1 까지만 동의한 후보, 둘 다 사진이 있다
        User me = participant(Gender.MALE, POOL, YESTERDAY);
        User agreed = participant(Gender.FEMALE, POOL, YESTERDAY);
        User onlyOld = participant(Gender.FEMALE, POOL, YESTERDAY);
        enableDisclosureVersion("1.2");
        agreeDisclosure(agreed.getId(), "1.2");

        String agreedKey = "profile/" + agreed.getId() + "/original";
        photoRepository.saveAndFlush(Photo.create(agreed.getId(), PhotoType.PROFILE, agreedKey, 1, 2, 30, 40, Instant.now()));
        Photo onlyOldPhoto = Photo.create(onlyOld.getId(), PhotoType.PROFILE, "profile/" + onlyOld.getId() + "/original", 0, 0, 100, 100, Instant.now());
        ReflectionTestUtils.setField(onlyOldPhoto, "previewKey", "preview/old-" + onlyOld.getId());
        photoRepository.saveAndFlush(onlyOldPhoto);

        // when: v2 미리보기 조회
        var result = mockMvc.perform(get("/api/v2/people/previews")
                .header("Authorization", bearer(me.getId())));

        // then: 1.2 동의자는 원본 서명 URL·position·닉네임, 1.1 동의자는 가린 사진만
        result.andExpect(status().isOk())
                .andExpect(jsonPath("$.previews.length()").value(2));
        List<Map<String, Object>> previews = JsonPath.read(result.andReturn().getResponse().getContentAsString(), "$.previews");

        Map<String, Object> agreedCard = previews.stream()
                .filter(preview -> agreed.getNickname().equals(preview.get("nickname"))).findFirst().orElseThrow();
        Map<String, Object> agreedPhoto = (Map<String, Object>) agreedCard.get("profilePhoto");
        assertThat((String) agreedPhoto.get("photoUrl")).startsWith("https://test.cloudfront.net/" + agreedKey);
        assertThat(agreedPhoto).containsOnlyKeys("photoUrl", "position");
        assertThat(JsonPath.<Integer>read(agreedPhoto, "$.position.startPos.x")).isEqualTo(1);
        assertThat(JsonPath.<Integer>read(agreedPhoto, "$.position.width")).isEqualTo(30);
        assertThat(agreedCard).containsEntry("maskedPhotoUrl", null);

        Map<String, Object> maskedCard = previews.stream().filter(preview -> preview != agreedCard).findFirst().orElseThrow();
        assertThat(maskedCard).containsEntry("nickname", null).containsEntry("profilePhoto", null);
        assertThat((String) maskedCard.get("maskedPhotoUrl")).startsWith("https://test.cloudfront.net/preview/old-" + onlyOld.getId());
    }

    @Test
    @DisplayName("미리보기 v2 조회: 미참여자에게는 1.2 이상 동의자도 가린 사진만 주고 닉네임은 주지 않는다")
    void previewsV2_non_participant_sees_masked_only() throws Exception {
        // given: 미참여자인 나, 1.2 에도 동의하고 사진이 있는 후보
        User me = poolUser(Gender.MALE, POOL);
        User agreed = participant(Gender.FEMALE, POOL, YESTERDAY);
        enableDisclosureVersion("1.2");
        agreeDisclosure(agreed.getId(), "1.2");
        Photo photo = Photo.create(agreed.getId(), PhotoType.PROFILE, "profile/" + agreed.getId() + "/original", 0, 0, 100, 100, Instant.now());
        ReflectionTestUtils.setField(photo, "previewKey", "preview/agreed-" + agreed.getId());
        photoRepository.saveAndFlush(photo);

        // when: v2 미리보기 조회
        var result = mockMvc.perform(get("/api/v2/people/previews")
                .header("Authorization", bearer(me.getId())));

        // then: 가린 사진만, 닉네임·원본 사진은 null
        result.andExpect(status().isOk())
                .andExpect(jsonPath("$.previews.length()").value(1))
                .andExpect(jsonPath("$.previews[0].nickname").isEmpty())
                .andExpect(jsonPath("$.previews[0].profilePhoto").isEmpty())
                .andExpect(jsonPath("$.previews[0].maskedPhotoUrl").value(startsWith("https://test.cloudfront.net/preview/agreed-")));
    }

    @Test
    @DisplayName("미리보기 v1·v2 조회: 같은 날 이미 받은 사람이 실신원 동의를 철회하면 다음 호출부터 카드가 빠진다")
    void previews_drop_person_who_revoked_on_same_day() throws Exception {
        // given: 참여자인 나와 후보 1명, 같은 날 v1·v2 로 한 번씩 받았다
        User me = participant(Gender.MALE, POOL, YESTERDAY);
        User candidate = participant(Gender.FEMALE, POOL, YESTERDAY);
        mockMvc.perform(get("/api/v1/people/previews").header("Authorization", bearer(me.getId())))
                .andExpect(jsonPath("$.previews.length()").value(1));
        mockMvc.perform(get("/api/v2/people/previews").header("Authorization", bearer(me.getId())))
                .andExpect(jsonPath("$.previews.length()").value(1));

        // when: 후보가 동의를 철회한 뒤 같은 날 다시 조회
        revokeDisclosure(candidate.getId());
        var v1 = mockMvc.perform(get("/api/v1/people/previews").header("Authorization", bearer(me.getId())));
        var v2 = mockMvc.perform(get("/api/v2/people/previews").header("Authorization", bearer(me.getId())));

        // then: v1·v2 모두 200 + 빈 목록
        v1.andExpect(status().isOk()).andExpect(jsonPath("$.previews.length()").value(0));
        v2.andExpect(status().isOk()).andExpect(jsonPath("$.previews.length()").value(0));
    }

    @Test
    @DisplayName("미리보기 v2 조회: 인증 없이 호출하면 401 을 반환한다")
    void previewsV2_without_auth_returns_401() throws Exception {
        // when: Authorization 헤더 없이 v2 미리보기 조회
        var result = mockMvc.perform(get("/api/v2/people/previews"));

        // then: 실제 시큐리티 필터가 막아 401
        result.andExpect(status().isUnauthorized());
    }

    private User participant(Gender gender, Integer poolNumber, Instant participatedInAt) {
        User user = poolUser(gender, poolNumber);
        participate(user, participatedInAt);
        agreeDisclosure(user.getId(), "1.1");
        return user;
    }

    private void participate(User user, Instant participatedInAt) {
        SeasonParticipation participation = SeasonParticipation.create(user.getId(), season.getId());
        ReflectionTestUtils.setField(participation, "participatedInAt", participatedInAt);
        seasonParticipationRepository.saveAndFlush(participation);
    }

    private void enableDisclosureVersion(String version) {
        entityManager.createNativeQuery("""
                        INSERT INTO policies (policy_name_id, version, is_required, effective_at)
                        SELECT id, :version, TRUE, :effectiveAt FROM policy_names WHERE identifier = :identifier
                        ON DUPLICATE KEY UPDATE effective_at = :effectiveAt
                        """)
                .setParameter("version", version)
                .setParameter("effectiveAt", Instant.now().minus(Duration.ofDays(1)))
                .setParameter("identifier", DISCLOSURE)
                .executeUpdate();
    }

    private void agreeDisclosure(Long userId, String version) {
        PolicySummary policy = policyCatalog.loadByKey(List.of(DISCLOSURE)).get(new PolicyKey(DISCLOSURE, version));
        agreementRepository.saveAndFlush(Agreement.create(userId, policy.getId(), Instant.now()));
    }

    private void revokeDisclosure(Long userId) {
        PolicySummary policy = policyCatalog.loadByKey(List.of(DISCLOSURE)).get(new PolicyKey(DISCLOSURE, "1.1"));
        agreementRepository.revokeWithPreviousVersionsByUserIdAndPolicyIdIn(userId, List.of(policy.getId()));
    }

    private User poolUser(Gender gender, Integer poolNumber) {
        User user = saveUser();
        ReflectionTestUtils.setField(user, "gender", gender);
        ReflectionTestUtils.setField(user, "poolNumber", poolNumber);
        return userRepository.saveAndFlush(user);
    }

    private void writeJpeg(Path destination) throws IOException {
        BufferedImage image = new BufferedImage(100, 100, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = image.createGraphics();
        try {
            graphics.setColor(Color.BLUE);
            graphics.fillRect(0, 0, 100, 100);
        } finally {
            graphics.dispose();
        }
        ImageIO.write(image, "jpg", destination.toFile());
    }

    private String pathOf(String url) {
        return url.substring(0, url.indexOf('?'));
    }
}
