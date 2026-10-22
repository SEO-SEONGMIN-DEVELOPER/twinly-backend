package com.nidus.twinly.simulation.client;

import com.nidus.twinly.common.web.BusinessException;
import com.nidus.twinly.common.web.ErrorCode;
import com.nidus.twinly.simulation.config.AiServerProperties;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.http.converter.json.JacksonJsonHttpMessageConverter;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import tools.jackson.databind.json.JsonMapper;

@Component
public class SimulationBatchCostClient {

    private final RestClient restClient;

    public SimulationBatchCostClient(JsonMapper jsonMapper, AiServerProperties aiServerProperties) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(aiServerProperties.connectTimeout());
        requestFactory.setReadTimeout(aiServerProperties.readTimeout());
        this.restClient = RestClient.builder()
                .baseUrl(aiServerProperties.baseUrl())
                .requestFactory(requestFactory)
                .configureMessageConverters(c -> c.withJsonConverter(new JacksonJsonHttpMessageConverter(jsonMapper)))
                .build();
    }

    public SimulationBatchCostBody fetch() {
        SimulationBatchCostBody body;
        try {
            body = restClient.get()
                    .uri("/internal/v1/admin/batch-costs")
                    .retrieve()
                    .body(SimulationBatchCostBody.class);
        } catch (RestClientException e) {
            throw new BusinessException(ErrorCode.SIMULATION_BATCH_COST_FETCH_FAILED, e);
        }
        if (body == null) {
            throw new BusinessException(ErrorCode.SIMULATION_BATCH_COST_FETCH_FAILED);
        }
        return body;
    }
}
