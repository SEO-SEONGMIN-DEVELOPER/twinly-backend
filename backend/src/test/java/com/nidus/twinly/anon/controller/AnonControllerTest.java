package com.nidus.twinly.anon.controller;

import com.nidus.twinly.anon.dto.AnonStartResult;
import com.nidus.twinly.anon.repository.AnonSessionRepository;
import com.nidus.twinly.anon.service.AnonService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AnonController.class)
class AnonControllerTest {
    @Autowired
    MockMvc mockMvc;

    @MockitoBean
    AnonService anonService;

    @MockitoBean
    AnonSessionRepository anonSessionRepository;

    @BeforeEach
    void setUp() {
        when(anonService.start())
                .thenReturn(new AnonStartResult(UUID.randomUUID(), 3600));
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