package com.nidus.twinly.common.monitoring;

import com.nidus.twinly.support.AbstractIntegrationTest;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.Arrays;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class HttpServerRequestsMetricsIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    MeterRegistry meterRegistry;

    @Test
    @DisplayName("요청 지표 http.server.requests 에 호출 주체 caller 태그가 붙는다")
    void http_server_requests_has_caller_tag() throws Exception {
        // when: 앱이 부르는 API 와, 어디에도 해당하지 않는 경로를 한 번씩 호출
        mockMvc.perform(get("/api/v1/app/status")).andExpect(status().isOk());
        mockMvc.perform(get("/robots.txt")).andExpect(status().isNotFound());

        // then: 경로에 따라 app·other 로 나뉘어 기록된다
        assertThat(meterRegistry.find("http.server.requests").tags("uri", "/api/v1/app/status", "caller", "app").timer())
                .isNotNull();
        assertThat(meterRegistry.find("http.server.requests").tags("caller", "other").timer())
                .isNotNull();
    }

    @Test
    @DisplayName("응답 시간 히스토그램은 30초까지 구간을 두어 Bedrock 처럼 느린 호출도 상한에 눌리지 않는다")
    void http_server_requests_histogram_reaches_30_seconds() throws Exception {
        // when
        mockMvc.perform(get("/api/v1/app/status")).andExpect(status().isOk());

        // then: 가장 큰 유한 구간이 30초다
        Timer timer = meterRegistry.find("http.server.requests").tags("uri", "/api/v1/app/status").timer();
        assertThat(timer).isNotNull();

        double maxBucketSeconds = Arrays.stream(timer.takeSnapshot().histogramCounts())
                .mapToDouble(bucket -> bucket.bucket(TimeUnit.SECONDS))
                .filter(Double::isFinite)
                .max()
                .orElseThrow();
        assertThat(maxBucketSeconds).isEqualTo(30.0);
    }
}
