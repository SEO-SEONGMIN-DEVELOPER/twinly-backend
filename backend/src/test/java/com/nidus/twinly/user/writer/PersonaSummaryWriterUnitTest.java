package com.nidus.twinly.user.writer;

import com.nidus.twinly.auth.event.UserSignedUpEvent;
import com.nidus.twinly.common.persona.PersonaDimension;
import com.nidus.twinly.common.web.BusinessException;
import com.nidus.twinly.common.web.ErrorCode;
import com.nidus.twinly.user.entity.PersonaElement;
import com.nidus.twinly.user.entity.User;
import com.nidus.twinly.user.generator.PersonaSummaryGenerator;
import com.nidus.twinly.user.repository.PersonaElementRepository;
import com.nidus.twinly.user.repository.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;

@ExtendWith(MockitoExtension.class)
class PersonaSummaryWriterUnitTest {

    private static final Long USER_ID = 100L;
    private static final UserSignedUpEvent EVENT = new UserSignedUpEvent(USER_ID);
    private static final Instant NOW = Instant.parse("2026-01-01T00:00:00Z");

    @Mock
    PersonaSummaryGenerator personaSummaryGenerator;

    @Mock
    UserRepository userRepository;

    @Mock
    PersonaElementRepository personaElementRepository;

    @InjectMocks
    PersonaSummaryWriter personaSummaryWriter;

    @Test
    @DisplayName("가입한 유저의 소속과 페르소나 요소로 요약을 만들어 SUMMARY 요소로 저장한다")
    void onUserSignedUp_saves_summary() {
        // given: 소속이 있는 유저와 관심사·대화 요소, 생성기가 요약을 반환
        givenUser("트윈리대학교");
        List<PersonaElement> elements = List.of(
                PersonaElement.create(USER_ID, PersonaDimension.INTEREST, "등산", NOW),
                PersonaElement.create(USER_ID, PersonaDimension.DETAIL, "요즘 뭐에 빠져 있어?: 등산", NOW));
        given(personaElementRepository.findAllByUserIdOrderByIdAsc(USER_ID)).willReturn(elements);
        given(personaSummaryGenerator.generate("트윈리대학교", elements)).willReturn("주말마다 산에 오르는 사람");

        // when
        personaSummaryWriter.onUserSignedUp(EVENT);

        // then: 유저 소유의 SUMMARY 요소로 저장됨
        ArgumentCaptor<PersonaElement> captor = ArgumentCaptor.forClass(PersonaElement.class);
        then(personaElementRepository).should().save(captor.capture());
        assertThat(captor.getValue().getUserId()).isEqualTo(USER_ID);
        assertThat(captor.getValue().getDimension()).isEqualTo(PersonaDimension.SUMMARY);
        assertThat(captor.getValue().getExplanation()).isEqualTo("주말마다 산에 오르는 사람");
        assertThat(captor.getValue().getCreatedAt()).isNotNull();
    }

    @Test
    @DisplayName("이미 SUMMARY 요소가 있으면 요약을 다시 만들지 않는다")
    void onUserSignedUp_skips_when_summary_exists() {
        // given
        given(userRepository.findById(USER_ID)).willReturn(Optional.of(mock(User.class)));
        given(personaElementRepository.findAllByUserIdOrderByIdAsc(USER_ID)).willReturn(List.of(
                PersonaElement.create(USER_ID, PersonaDimension.INTEREST, "등산", NOW),
                PersonaElement.create(USER_ID, PersonaDimension.SUMMARY, "이미 만든 요약", NOW)));

        // when
        personaSummaryWriter.onUserSignedUp(EVENT);

        // then
        then(personaSummaryGenerator).shouldHaveNoInteractions();
        then(personaElementRepository).should(never()).save(any());
    }

    @Test
    @DisplayName("페르소나 요소가 하나도 없으면 근거 없는 요약을 만들지 않도록 모델을 호출하지 않는다")
    void onUserSignedUp_skips_when_no_persona_elements() {
        // given
        given(userRepository.findById(USER_ID)).willReturn(Optional.of(mock(User.class)));
        given(personaElementRepository.findAllByUserIdOrderByIdAsc(USER_ID)).willReturn(List.of());

        // when
        personaSummaryWriter.onUserSignedUp(EVENT);

        // then
        then(personaSummaryGenerator).shouldHaveNoInteractions();
        then(personaElementRepository).should(never()).save(any());
    }

    @Test
    @DisplayName("비동기 실행 전에 유저가 사라졌으면 아무것도 하지 않는다")
    void onUserSignedUp_skips_when_user_missing() {
        // given
        given(userRepository.findById(USER_ID)).willReturn(Optional.empty());

        // when
        personaSummaryWriter.onUserSignedUp(EVENT);

        // then
        then(personaElementRepository).shouldHaveNoInteractions();
        then(personaSummaryGenerator).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("요약 생성이 실패하면 예외를 밖으로 던지지 않고 저장도 하지 않는다 (프로필은 fallback 문구로 표시)")
    void onUserSignedUp_does_not_throw_when_generation_fails() {
        // given
        givenUser(null);
        given(personaElementRepository.findAllByUserIdOrderByIdAsc(USER_ID)).willReturn(List.of(
                PersonaElement.create(USER_ID, PersonaDimension.INTEREST, "등산", NOW)));
        given(personaSummaryGenerator.generate(any(), anyList()))
                .willThrow(new BusinessException(ErrorCode.AI_RESPONSE_FAILED));

        // when & then
        assertThatCode(() -> personaSummaryWriter.onUserSignedUp(EVENT)).doesNotThrowAnyException();
        then(personaElementRepository).should(never()).save(any());
    }

    private void givenUser(String affiliation) {
        User user = mock(User.class);
        given(user.getAffiliation()).willReturn(affiliation);
        given(userRepository.findById(USER_ID)).willReturn(Optional.of(user));
    }
}
