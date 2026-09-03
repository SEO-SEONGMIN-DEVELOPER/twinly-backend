package com.nidus.twinly.app.integration;

import com.nidus.twinly.app.domain.MaintenanceState;
import com.nidus.twinly.app.store.AppBlockPolicyStore;
import com.nidus.twinly.common.web.ErrorCode;
import com.nidus.twinly.support.AbstractIntegrationTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpHeaders;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AppStatusIntegrationTest extends AbstractIntegrationTest {

    private static final String STATUS_PATH = "/api/v1/app/status";

    @Autowired
    AppBlockPolicyStore appBlockPolicyStore;

    @Autowired
    StringRedisTemplate redisTemplate;

    @AfterEach
    void clearMaintenance() {
        appBlockPolicyStore.saveMaintenance(MaintenanceState.none());
        redisTemplate.delete("app:maintenance");
    }

    @Test
    @DisplayName("토큰 없이 호출해도 200과 빈 body를 돌려주고, 어디에도 저장되지 않게 no-store를 붙인다")
    void status_withoutToken_returns200NoStore() throws Exception {
        mockMvc.perform(get(STATUS_PATH))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-store"))
                .andExpect(content().json("{}"));
    }

    @Test
    @DisplayName("점검 중이면 probe도 필터에서 503 MAINTENANCE로 끝난다")
    void status_duringMaintenance_returns503() throws Exception {
        // given
        appBlockPolicyStore.saveMaintenance(new MaintenanceState(true, "점검 중이에요.", null));

        // when & then
        mockMvc.perform(get(STATUS_PATH))
                .andExpect(status().isServiceUnavailable())
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-store"))
                .andExpect(jsonPath("$.code").value(ErrorCode.MAINTENANCE.name()))
                .andExpect(jsonPath("$.message").value("점검 중이에요."))
                .andExpect(jsonPath("$.until").value(org.hamcrest.Matchers.nullValue()));
    }
}
