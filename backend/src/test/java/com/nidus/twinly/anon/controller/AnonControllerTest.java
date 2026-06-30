package com.nidus.twinly.anon.controller;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.web.servlet.MockMvc;
import com.jayway.jsonpath.JsonPath;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import com.nidus.twinly.anon.service.AnonService;
import com.nidus.twinly.anon.dto.AnonStartResponse;

import static org.mockito.Mockito.when;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;

@WebMvcTest
class AnonControllerTest {
    @Autowired
    MockMvc mockMvc;

    @MockitoBean
    AnonService anonService;

    @BeforeEach
    void setUp() {
        when(anonService.start())
                .thenReturn(new AnonStartResponse(UUID.randomUUID(), 3600));
    }

    @Test
    void start_returns_200() throws Exception {
        mockMvc.perform(post("/api/v1/anon/start"))
                .andExpect(status().isOk());
    }

    @Test
    void start_returns_token_and_expiry() throws Exception {
        mockMvc.perform(post("/api/v1/anon/start"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.anonSessionToken").exists())
                .andExpect(jsonPath("$.expiresInSec").exists());
    }
}