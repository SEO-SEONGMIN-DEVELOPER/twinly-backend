package com.nidus.twinly.common.slack;

import com.nidus.twinly.common.web.BusinessException;
import com.nidus.twinly.common.web.ErrorCode;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.http.converter.json.JacksonJsonHttpMessageConverter;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import tools.jackson.databind.json.JsonMapper;

import java.net.URI;

@Component
public class SlackClient {

    private final RestClient restClient;
    private final URI reportWebhookUri;

    public SlackClient(JsonMapper jsonMapper, SlackProperties slackProperties) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(slackProperties.connectTimeout());
        requestFactory.setReadTimeout(slackProperties.readTimeout());
        this.restClient = RestClient.builder()
                .requestFactory(requestFactory)
                .configureMessageConverters(c -> c.withJsonConverter(new JacksonJsonHttpMessageConverter(jsonMapper)))
                .build();
        this.reportWebhookUri = URI.create(slackProperties.reportWebhookUrl());
    }

    public void sendReportAlert(String text) {
        try {
            restClient.post()
                    .uri(reportWebhookUri)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(new SlackMessageRequest(text))
                    .retrieve()
                    .toBodilessEntity();
        } catch (RestClientException e) {
            throw new BusinessException(ErrorCode.SLACK_SEND_FAILED, e);
        }
    }
}
