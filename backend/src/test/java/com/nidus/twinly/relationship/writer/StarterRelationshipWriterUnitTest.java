package com.nidus.twinly.relationship.writer;

import com.nidus.twinly.common.domain.Gender;
import com.nidus.twinly.common.time.KstTimes;
import com.nidus.twinly.people.repository.EncounterRepository;
import com.nidus.twinly.relationship.entity.Relationship;
import com.nidus.twinly.relationship.repository.RelationshipRepository;
import com.nidus.twinly.season.repository.SeasonParticipationRepository;
import com.nidus.twinly.season.repository.SeasonParticipationRepository.StarterCandidateProjection;
import com.nidus.twinly.user.entity.User;
import com.nidus.twinly.user.repository.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.BeanUtils;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

@ExtendWith(MockitoExtension.class)
class StarterRelationshipWriterUnitTest {

    private static final Long USER_ID = 10L;
    private static final Long SEASON_ID = 1L;
    private static final Integer POOL_NUMBER = 13;
    private static final Instant PARTICIPATED_AT = Instant.parse("2026-10-29T03:00:00Z");

    @Mock
    UserRepository userRepository;

    @Mock
    SeasonParticipationRepository seasonParticipationRepository;

    @Mock
    RelationshipRepository relationshipRepository;

    @Mock
    EncounterRepository encounterRepository;

    @InjectMocks
    StarterRelationshipWriter starterRelationshipWriter;

    @Test
    @DisplayName("이성 후보가 충분하면 이성 2명과 양방향 0점 관계를 참가 전날 날짜로 만들고 encounter 를 남긴다")
    void connect_picks_two_opposite_gender_and_saves_both_directions() {
        // given: 남자 유저, 같은 풀에 여자 후보 3명과 남자 후보 2명
        givenUser(Gender.MALE, POOL_NUMBER);
        given(relationshipRepository.existsByUserId(USER_ID)).willReturn(false);
        given(seasonParticipationRepository.findAllStarterCandidates(USER_ID, SEASON_ID, POOL_NUMBER)).willReturn(List.of(
                candidate(21L, Gender.FEMALE), candidate(22L, Gender.FEMALE), candidate(23L, Gender.FEMALE),
                candidate(31L, Gender.MALE), candidate(32L, Gender.MALE)));

        // when: 시작 인연 연결
        starterRelationshipWriter.connect(USER_ID, SEASON_ID, PARTICIPATED_AT);

        // then: 여자 후보 중 2명과 양방향 4행이 저장되고, 모두 친밀도 0·참가 전날·참가 시각 기준이다
        List<Relationship> saved = capturedRelationships();
        assertThat(saved).hasSize(4);
        List<Long> partnerUserIds = saved.stream()
                .filter(relationship -> relationship.getUserId().equals(USER_ID))
                .map(Relationship::getPartnerUserId)
                .toList();
        assertThat(partnerUserIds).hasSize(2).isSubsetOf(21L, 22L, 23L);
        assertThat(saved)
                .extracting(Relationship::getUserId, Relationship::getPartnerUserId)
                .containsExactlyInAnyOrder(
                        tuple(USER_ID, partnerUserIds.get(0)), tuple(partnerUserIds.get(0), USER_ID),
                        tuple(USER_ID, partnerUserIds.get(1)), tuple(partnerUserIds.get(1), USER_ID));
        assertThat(saved).allSatisfy(relationship -> {
            assertThat(relationship.getIntimacy()).isZero();
            assertThat(relationship.getVersion()).isEqualTo("starter");
            assertThat(relationship.getDate()).isEqualTo(KstTimes.toLocalDateTime(PARTICIPATED_AT).toLocalDate().minusDays(1));
            assertThat(relationship.getUpdateTime()).isEqualTo(KstTimes.toLocalDateTime(PARTICIPATED_AT));
            assertThat(relationship.getIntimacyAsOf()).isEqualTo(PARTICIPATED_AT);
        });
        partnerUserIds.forEach(partnerUserId ->
                then(encounterRepository).should().upsert(Math.min(USER_ID, partnerUserId), Math.max(USER_ID, partnerUserId)));
    }

    @Test
    @DisplayName("이성 후보가 1명뿐이면 그 1명과 동성 1명을 연결한다")
    void connect_fills_with_same_gender_when_opposite_is_short() {
        // given: 남자 유저, 같은 풀에 여자 후보 1명과 남자 후보 2명
        givenUser(Gender.MALE, POOL_NUMBER);
        given(relationshipRepository.existsByUserId(USER_ID)).willReturn(false);
        given(seasonParticipationRepository.findAllStarterCandidates(USER_ID, SEASON_ID, POOL_NUMBER)).willReturn(List.of(
                candidate(21L, Gender.FEMALE), candidate(31L, Gender.MALE), candidate(32L, Gender.MALE)));

        // when: 시작 인연 연결
        starterRelationshipWriter.connect(USER_ID, SEASON_ID, PARTICIPATED_AT);

        // then: 여자 1명은 반드시 들어가고 나머지 1명은 남자 후보 중에서 채워진다
        List<Long> partnerUserIds = capturedRelationships().stream()
                .filter(relationship -> relationship.getUserId().equals(USER_ID))
                .map(Relationship::getPartnerUserId)
                .toList();
        assertThat(partnerUserIds).hasSize(2).contains(21L);
        assertThat(partnerUserIds).filteredOn(id -> !id.equals(21L)).singleElement().isIn(31L, 32L);
    }

    @Test
    @DisplayName("이성 후보가 없으면 동성 후보로만 2명을 연결한다")
    void connect_uses_same_gender_when_no_opposite() {
        // given: 여자 유저, 같은 풀에 여자 후보 3명뿐
        givenUser(Gender.FEMALE, POOL_NUMBER);
        given(relationshipRepository.existsByUserId(USER_ID)).willReturn(false);
        given(seasonParticipationRepository.findAllStarterCandidates(USER_ID, SEASON_ID, POOL_NUMBER)).willReturn(List.of(
                candidate(21L, Gender.FEMALE), candidate(22L, Gender.FEMALE), candidate(23L, Gender.FEMALE)));

        // when: 시작 인연 연결
        starterRelationshipWriter.connect(USER_ID, SEASON_ID, PARTICIPATED_AT);

        // then: 동성 후보 2명과 양방향 4행
        assertThat(capturedRelationships()).hasSize(4);
    }

    @Test
    @DisplayName("이미 인연이 있는 유저면 후보를 찾지 않고 아무것도 만들지 않는다")
    void connect_skips_when_user_already_has_relationship() {
        // given: 이전 시즌 등으로 인연이 이미 있는 유저
        givenUser(Gender.MALE, POOL_NUMBER);
        given(relationshipRepository.existsByUserId(USER_ID)).willReturn(true);

        // when: 시작 인연 연결
        starterRelationshipWriter.connect(USER_ID, SEASON_ID, PARTICIPATED_AT);

        // then: 후보 조회·저장·encounter 모두 없음
        then(seasonParticipationRepository).should(never()).findAllStarterCandidates(any(), any(), any());
        then(relationshipRepository).should(never()).saveAll(any());
        then(encounterRepository).should(never()).upsert(anyLong(), anyLong());
    }

    @Test
    @DisplayName("풀 번호가 없는 유저면 아무것도 만들지 않는다")
    void connect_skips_when_pool_number_missing() {
        // given: 풀이 배정되지 않은 유저
        givenUser(Gender.MALE, null);

        // when: 시작 인연 연결
        starterRelationshipWriter.connect(USER_ID, SEASON_ID, PARTICIPATED_AT);

        // then: 후보 조회·저장 없음
        then(seasonParticipationRepository).should(never()).findAllStarterCandidates(any(), any(), any());
        then(relationshipRepository).should(never()).saveAll(any());
    }

    @Test
    @DisplayName("같은 풀에 후보가 없으면 저장하지 않는다")
    void connect_skips_when_no_candidates() {
        // given: 같은 풀에 연결할 후보가 없음
        givenUser(Gender.MALE, POOL_NUMBER);
        given(relationshipRepository.existsByUserId(USER_ID)).willReturn(false);
        given(seasonParticipationRepository.findAllStarterCandidates(USER_ID, SEASON_ID, POOL_NUMBER)).willReturn(List.of());

        // when: 시작 인연 연결
        starterRelationshipWriter.connect(USER_ID, SEASON_ID, PARTICIPATED_AT);

        // then: 저장·encounter 없음
        then(relationshipRepository).should(never()).saveAll(any());
        then(encounterRepository).should(never()).upsert(anyLong(), anyLong());
    }

    @SuppressWarnings("unchecked")
    private List<Relationship> capturedRelationships() {
        ArgumentCaptor<List<Relationship>> captor = ArgumentCaptor.forClass(List.class);
        then(relationshipRepository).should().saveAll(captor.capture());
        return captor.getValue();
    }

    private void givenUser(Gender gender, Integer poolNumber) {
        User user = BeanUtils.instantiateClass(User.class);
        ReflectionTestUtils.setField(user, "id", USER_ID);
        ReflectionTestUtils.setField(user, "gender", gender);
        ReflectionTestUtils.setField(user, "poolNumber", poolNumber);
        given(userRepository.findById(USER_ID)).willReturn(Optional.of(user));
    }

    private StarterCandidateProjection candidate(Long userId, Gender gender) {
        return new StarterCandidateProjection() {

            @Override
            public Long getUserId() {
                return userId;
            }

            @Override
            public Gender getGender() {
                return gender;
            }
        };
    }
}
