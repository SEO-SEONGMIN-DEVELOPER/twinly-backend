package com.nidus.twinly.simulation.client;

import com.nidus.twinly.common.web.BusinessException;
import com.nidus.twinly.common.web.ErrorCode;
import com.nidus.twinly.simulation.config.AiServerProperties;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.http.converter.json.JacksonJsonHttpMessageConverter;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import tools.jackson.databind.json.JsonMapper;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Component
public class SimulationPreloadClient {

    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(2);
    private static final Duration READ_TIMEOUT = Duration.ofSeconds(3);

    private final RestClient restClient;

    public SimulationPreloadClient(JsonMapper jsonMapper, AiServerProperties aiServerProperties) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(CONNECT_TIMEOUT);
        requestFactory.setReadTimeout(READ_TIMEOUT);
        this.restClient = RestClient.builder()
                .baseUrl(aiServerProperties.baseUrl())
                .requestFactory(requestFactory)
                .configureMessageConverters(c -> c.withJsonConverter(new JacksonJsonHttpMessageConverter(jsonMapper)))
                .build();
    }

    public void preload(Long userId, LocalDateTime grantedAt, List<LocalDate> dates) {
        try {
            restClient.post()
                    .uri("/internal/v1/simulations/preload")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(new SimulationPreloadRequest(userId, grantedAt, dates))
                    .retrieve()
                    .toBodilessEntity();
        } catch (RestClientException e) {
            throw new BusinessException(ErrorCode.SIMULATION_PRELOAD_FAILED, e);
        }
    }
}
