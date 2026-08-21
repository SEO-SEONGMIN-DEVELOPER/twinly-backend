package com.nidus.twinly.simulation.service;

import com.nidus.twinly.activity.repository.QuestionPartnerRepository;
import com.nidus.twinly.activity.repository.QuestionRepository;
import com.nidus.twinly.activity.repository.ScenePartnerRepository;
import com.nidus.twinly.activity.repository.SceneRepository;
import com.nidus.twinly.common.domain.Gender;
import com.nidus.twinly.common.persona.PersonaDimension;
import com.nidus.twinly.common.web.BusinessException;
import com.nidus.twinly.common.web.ErrorCode;
import com.nidus.twinly.chat.opener.ChatRoomOpener;
import com.nidus.twinly.notification.writer.AppNotificationFeedWriter;
import com.nidus.twinly.people.repository.EncounterRepository;
import com.nidus.twinly.relationship.entity.Relationship;
import com.nidus.twinly.relationship.repository.RelationshipRepository;
import com.nidus.twinly.simulation.dto.command.SimulationsCommand;
import com.nidus.twinly.simulation.dto.command.SimulationsRelationshipCommand;
import com.nidus.twinly.simulation.dto.result.SimulationPersonaResult;
import com.nidus.twinly.user.entity.PersonaElement;
import com.nidus.twinly.user.entity.User;
import com.nidus.twinly.user.repository.PersonaElementRepository;
import com.nidus.twinly.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.BeanUtils;
import org.springframework.test.util.ReflectionTestUtils;
import tools.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

@ExtendWith(MockitoExtension.class)
class SimulationServiceUnitTest {

    private static final Long USER_ID = 12L;
    private static final Long PARTNER_ID = 34L;
    private static final LocalDate DATE = LocalDate.of(2026, 8, 3);

    @Mock
    SceneRepository sceneRepository;

    @Mock
    ScenePartnerRepository scenePartnerRepository;

    @Mock
    QuestionRepository questionRepository;

    @Mock
    QuestionPartnerRepository questionPartnerRepository;

    @Mock
    RelationshipRepository relationshipRepository;

    @Mock
    EncounterRepository encounterRepository;

    @Mock
    ChatRoomOpener chatRoomOpener;

    @Mock
    AppNotificationFeedWriter appNotificationFeedWriter;

    @Mock
    UserRepository userRepository;

    @Mock
    PersonaElementRepository personaElementRepository;

    SimulationService simulationService;

    @BeforeEach
    void setUp() {
        simulationService = new SimulationService(
                sceneRepository, scenePartnerRepository, questionRepository, questionPartnerRepository,
                relationshipRepository, encounterRepository, chatRoomOpener, appNotificationFeedWriter, userRepository,
                personaElementRepository, new ObjectMapper());
    }

    @Test
    @DisplayName("친밀도가 친구 기준을 처음 넘으면 그 날짜로 friend 피드를 남긴다")
    void simulations_writes_friend_feed_when_relationship_type_becomes_friend() {
        // given: 직전 관계는 지인(20)이고 이번 시뮬레이션에서 친구 기준(35)을 넘김
        givenEmptyPreviousSimulation();
        given(relationshipRepository.findLatestByUserIdAndPartnerUserIdBeforeDate(USER_ID, PARTNER_ID, DATE))
                .willReturn(Optional.of(relationship(20)));

        // when: 시뮬레이션 결과 저장
        simulationService.simulations(USER_ID, simulationsCommand(35));

        // then: 시뮬레이션 날짜를 출처로 friend 피드를 남김
        then(appNotificationFeedWriter).should().writeFriend(USER_ID, PARTNER_ID, DATE);
    }

    @Test
    @DisplayName("직전 관계가 없어도 첫 시뮬레이션에서 친구 기준을 넘으면 friend 피드를 남긴다")
    void simulations_writes_friend_feed_without_previous_relationship() {
        // given: 직전 관계 기록이 없음
        givenEmptyPreviousSimulation();
        given(relationshipRepository.findLatestByUserIdAndPartnerUserIdBeforeDate(USER_ID, PARTNER_ID, DATE))
                .willReturn(Optional.empty());

        // when: 친구 기준을 넘는 친밀도로 저장
        simulationService.simulations(USER_ID, simulationsCommand(35));

        // then: 지인에서 올라온 것으로 보고 피드를 남김
        then(appNotificationFeedWriter).should().writeFriend(USER_ID, PARTNER_ID, DATE);
    }

    @Test
    @DisplayName("이미 친구 이상이던 상대는 친밀도가 더 올라도 friend 피드를 남기지 않는다")
    void simulations_does_not_write_friend_feed_when_already_friend() {
        // given: 직전 관계가 이미 친구(40)
        givenEmptyPreviousSimulation();
        given(relationshipRepository.findLatestByUserIdAndPartnerUserIdBeforeDate(USER_ID, PARTNER_ID, DATE))
                .willReturn(Optional.of(relationship(40)));

        // when: 친밀도가 더 오른 채로 저장
        simulationService.simulations(USER_ID, simulationsCommand(60));

        // then: 경계를 새로 넘은 것이 아니므로 피드 없음
        then(appNotificationFeedWriter).should(never()).writeFriend(any(), any(), any());
    }

    @Test
    @DisplayName("친구 기준에 못 미치면 friend 피드를 남기지 않는다")
    void simulations_does_not_write_friend_feed_below_threshold() {
        // given: 직전 관계도 지인이고 이번에도 지인
        givenEmptyPreviousSimulation();
        given(relationshipRepository.findLatestByUserIdAndPartnerUserIdBeforeDate(USER_ID, PARTNER_ID, DATE))
                .willReturn(Optional.of(relationship(10)));

        // when: 친구 기준 미만으로 저장
        simulationService.simulations(USER_ID, simulationsCommand(34));

        // then: 피드 없음
        then(appNotificationFeedWriter).should(never()).writeFriend(any(), any(), any());
    }

    private void givenEmptyPreviousSimulation() {
        given(userRepository.existsById(USER_ID)).willReturn(true);
        given(sceneRepository.findAllByUserIdAndDate(USER_ID, DATE)).willReturn(List.of());
        given(questionRepository.findAllByUserIdAndDate(USER_ID, DATE)).willReturn(List.of());
        given(sceneRepository.saveAll(any())).willReturn(List.of());
        given(questionRepository.saveAll(any())).willReturn(List.of());
    }

    private SimulationsCommand simulationsCommand(Integer rapport) {
        return new SimulationsCommand(USER_ID, DATE, List.of(), List.of(), List.of(
                new SimulationsRelationshipCommand(PARTNER_ID, DATE.atTime(21, 0), rapport, "{}")
        ));
    }

    private Relationship relationship(Integer intimacy) {
        Relationship relationship = BeanUtils.instantiateClass(Relationship.class);
        ReflectionTestUtils.setField(relationship, "intimacy", intimacy);
        return relationship;
    }

    @Test
    @DisplayName("유저 기본 정보와 성향을 차원별로 묶어 반환하고 birthDate를 LocalDate로 변환한다")
    void persona_groups_elements_by_dimension() {
        // given: 성향 4건(관심사 2건 포함)을 가진 유저
        given(userRepository.findById(USER_ID)).willReturn(Optional.of(user(USER_ID, "서", "성민", "컴퓨터공학과", "1999-03-21")));
        given(personaElementRepository.findAllByUserIdOrderByIdAsc(USER_ID)).willReturn(List.of(
                personaElement(PersonaDimension.OPENNESS, "새로운 시도를 즐긴다"),
                personaElement(PersonaDimension.CONFLICT_STYLE, "직접 말하기보다 시간을 둔다"),
                personaElement(PersonaDimension.INTEREST, "등산"),
                personaElement(PersonaDimension.INTEREST, "재즈")
        ));

        // when: 페르소나 조회
        SimulationPersonaResult result = simulationService.persona(USER_ID);

        // then: 기본 정보가 매핑되고 성향은 차원별로 묶임
        assertThat(result.userId()).isEqualTo(USER_ID);
        assertThat(result.familyName()).isEqualTo("서");
        assertThat(result.givenName()).isEqualTo("성민");
        assertThat(result.gender()).isEqualTo(Gender.MALE);
        assertThat(result.organization()).isEqualTo("성균관대학교");
        assertThat(result.affiliation()).isEqualTo("컴퓨터공학과");
        assertThat(result.birthDate()).isEqualTo(LocalDate.of(1999, 3, 21));
        assertThat(result.personaElements())
                .containsEntry(PersonaDimension.OPENNESS, List.of("새로운 시도를 즐긴다"))
                .containsEntry(PersonaDimension.CONFLICT_STYLE, List.of("직접 말하기보다 시간을 둔다"))
                .containsEntry(PersonaDimension.INTEREST, List.of("등산", "재즈"));
    }

    @Test
    @DisplayName("성향이 하나도 없으면 personaElements는 빈 Map이다")
    void persona_without_elements_returns_empty_map() {
        // given: 성향이 한 건도 없는 유저
        given(userRepository.findById(USER_ID)).willReturn(Optional.of(user(USER_ID, "서", "성민", "컴퓨터공학과", "1999-03-21")));
        given(personaElementRepository.findAllByUserIdOrderByIdAsc(USER_ID)).willReturn(List.of());

        // when: 페르소나 조회
        SimulationPersonaResult result = simulationService.persona(USER_ID);

        // then: 성향은 빈 Map
        assertThat(result.personaElements()).isEmpty();
    }

    @Test
    @DisplayName("존재하지 않는 유저면 USER_NOT_FOUND 예외가 발생하고 성향을 조회하지 않는다")
    void persona_user_not_found_throws() {
        // given: 해당 id의 유저 없음
        given(userRepository.findById(USER_ID)).willReturn(Optional.empty());

        // when & then: USER_NOT_FOUND 예외 발생 + 성향 조회 안 함
        assertThatThrownBy(() -> simulationService.persona(USER_ID))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.USER_NOT_FOUND);

        then(personaElementRepository).should(never()).findAllByUserIdOrderByIdAsc(any());
    }

    @Test
    @DisplayName("탈퇴한 유저면 존재하지 않는 유저와 동일하게 USER_NOT_FOUND 예외가 발생한다")
    void persona_withdrawn_user_throws() {
        // given: 탈퇴 처리된 유저
        User withdrawn = user(USER_ID, "서", "성민", "컴퓨터공학과", "1999-03-21");
        ReflectionTestUtils.setField(withdrawn, "deletedAt", Instant.parse("2026-08-01T00:00:00Z"));
        given(userRepository.findById(USER_ID)).willReturn(Optional.of(withdrawn));

        // when & then: USER_NOT_FOUND 예외 발생 + 성향 조회 안 함
        assertThatThrownBy(() -> simulationService.persona(USER_ID))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.USER_NOT_FOUND);

        then(personaElementRepository).should(never()).findAllByUserIdOrderByIdAsc(any());
    }

    private User user(Long id, String familyName, String givenName, String affiliation, String birthDate) {
        User user = User.create(
                "nickname", familyName, "familyNameHash", givenName, "givenNameHash", Gender.MALE,
                "성균관대학교", "organizationHash",
                affiliation, "affiliationHash", "20191234", "affiliationNumberHash",
                birthDate, "birthDateHash", "01012345678", "phoneNumberHash", "a@b.ac.kr", "emailHash");
        ReflectionTestUtils.setField(user, "id", id);

        return user;
    }

    private PersonaElement personaElement(PersonaDimension dimension, String explanation) {
        return PersonaElement.create(USER_ID, dimension, explanation, Instant.parse("2026-08-01T00:00:00Z"));
    }
}
