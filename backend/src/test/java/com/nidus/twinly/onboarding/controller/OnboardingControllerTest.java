package com.nidus.twinly.onboarding.controller;

import com.nidus.twinly.anon.entity.AnonSession;
import com.nidus.twinly.anon.repository.AnonSessionRepository;
import com.nidus.twinly.onboarding.service.OnboardingService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(OnboardingController.class)
class OnboardingControllerTest {
    @Autowired
    MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    OnboardingService onboardingService;

    @MockitoBean
    AnonSessionRepository anonSessionRepository;

    private UUID token;

    @BeforeEach
    void setUp() {
        token = UUID.randomUUID();
        AnonSession anonSession = AnonSession.create(token, Instant.now().plusSeconds(3600));

        when(anonSessionRepository.findByToken(token)).thenReturn(Optional.of(anonSession));
    }

    @Test
    void basicInfo_returns_200() throws Exception {
        Map<String, Object> body = new HashMap<>();
        body.put("familyName", "familyName");
        body.put("givenName", "givenName");
        body.put("gender", "MALE");
        body.put("affiliation", "affiliation");
        body.put("affiliationNumber", "affiliationNumber");
        body.put("experience", "experience");
        body.put("birthDate", "birthDate");

        mockMvc.perform(post("/api/v1/onboarding/basic-info")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isOk());
    }
}