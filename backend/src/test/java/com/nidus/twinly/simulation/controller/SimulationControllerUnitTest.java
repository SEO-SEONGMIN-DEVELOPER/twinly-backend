package com.nidus.twinly.simulation.controller;

import com.nidus.twinly.anon.service.AnonService;
import com.nidus.twinly.common.domain.Gender;
import com.nidus.twinly.common.persona.PersonaDimension;
import com.nidus.twinly.common.web.BusinessException;
import com.nidus.twinly.common.web.ErrorCode;
import com.nidus.twinly.simulation.dto.result.SimulationPersonaResult;
import com.nidus.twinly.simulation.service.SimulationService;
import com.nidus.twinly.user.service.UserService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import com.nidus.twinly.common.security.SecurityConfig;
import org.springframework.context.annotation.Import;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(SimulationController.class)
@Import(SecurityConfig.class)
class SimulationControllerUnitTest {

    private static final Long USER_ID = 12L;

    @Autowired
    MockMvc mockMvc;

    @MockitoBean
    SimulationService simulationService;

    // SecurityConfig가 JWT·익명 세션 필터를 함께 만들고 각 필터가 이 서비스에 의존하므로 슬라이스 기동에 둘 다 필수.
    @MockitoBean
    UserService userService;

    @MockitoBean
    AnonService anonService;

    @Test
    @DisplayName("페르소나 조회 성공 시 200과 함께 userId를 문자열로, personaElements를 차원별 키로 직렬화한다")
    void persona_success() throws Exception {
        // given: 성향 3종을 가진 유저의 페르소나 조회 결과
        Map<PersonaDimension, List<String>> personaElements = new LinkedHashMap<>();
        personaElements.put(PersonaDimension.OPENNESS, List.of("새로운 시도를 즐긴다"));
        personaElements.put(PersonaDimension.CONFLICT_STYLE, List.of("직접 말하기보다 시간을 둔다"));
        personaElements.put(PersonaDimension.INTERESTS, List.of("등산", "재즈"));
        given(simulationService.persona(USER_ID)).willReturn(new SimulationPersonaResult(
                USER_ID, "서", "성민", Gender.MALE, "성균관대학교", "컴퓨터공학과", LocalDate.of(1999, 3, 21), personaElements));

        // when: 경로 변수 userId로 페르소나 조회 API 호출
        var result = mockMvc.perform(get("/internal/v1/users/{userId}/persona", "12"));

        // then: 200 반환 + 숫자 id는 문자열로, 성향은 차원별 키로 직렬화
        result.andExpect(status().isOk())
                .andExpect(jsonPath("$.userId", is("12")))
                .andExpect(jsonPath("$.familyName", is("서")))
                .andExpect(jsonPath("$.givenName", is("성민")))
                .andExpect(jsonPath("$.gender", is("male")))
                .andExpect(jsonPath("$.school", is("성균관대학교")))
                .andExpect(jsonPath("$.affiliation", is("컴퓨터공학과")))
                .andExpect(jsonPath("$.birthDate", is("1999-03-21")))
                .andExpect(jsonPath("$.personaElements.openness", hasSize(1)))
                .andExpect(jsonPath("$.personaElements.openness[0]", is("새로운 시도를 즐긴다")))
                .andExpect(jsonPath("$.personaElements.conflictStyle[0]", is("직접 말하기보다 시간을 둔다")))
                .andExpect(jsonPath("$.personaElements.interests", hasSize(2)));
        then(simulationService).should().persona(USER_ID);
    }

    @Test
    @DisplayName("성향이 하나도 없으면 personaElements를 빈 객체로 반환한다")
    void persona_without_elements_returns_empty_object() throws Exception {
        // given: 성향이 하나도 없는 유저의 페르소나 조회 결과
        given(simulationService.persona(USER_ID)).willReturn(new SimulationPersonaResult(
                USER_ID, "서", "성민", Gender.MALE, "성균관대학교", "컴퓨터공학과", LocalDate.of(1999, 3, 21), Map.of()));

        // when: 페르소나 조회 API 호출
        var result = mockMvc.perform(get("/internal/v1/users/{userId}/persona", "12"));

        // then: 200 반환 + personaElements는 빈 객체
        result.andExpect(status().isOk())
                .andExpect(jsonPath("$.personaElements").isEmpty());
    }

    @Test
    @DisplayName("경로 변수 userId가 숫자가 아니면 400을 반환하고 서비스를 호출하지 않는다")
    void persona_with_non_numeric_userId_returns_400() throws Exception {
        // when: 경로 변수 userId를 숫자가 아닌 값으로 페르소나 조회 API 호출
        var result = mockMvc.perform(get("/internal/v1/users/{userId}/persona", "abc"));

        // then: 400 반환 + 서비스는 호출되지 않음
        result.andExpect(status().isBadRequest());
        then(simulationService).should(never()).persona(anyLong());
    }

    @Test
    @DisplayName("서비스가 USER_NOT_FOUND를 던지면 404를 반환한다")
    void persona_user_not_found_returns_404() throws Exception {
        // given: 조회 대상이 없거나 탈퇴한 유저
        given(simulationService.persona(USER_ID)).willThrow(new BusinessException(ErrorCode.USER_NOT_FOUND));

        // when: 페르소나 조회 API 호출
        var result = mockMvc.perform(get("/internal/v1/users/{userId}/persona", "12"));

        // then: 404 반환
        result.andExpect(status().isNotFound());
    }
}
