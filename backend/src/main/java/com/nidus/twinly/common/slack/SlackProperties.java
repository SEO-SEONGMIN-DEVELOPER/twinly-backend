package com.nidus.twinly.common.slack;

import com.nidus.twinly.common.config.RequiredProperty;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "slack")
public record SlackProperties(
        String reportWebhookUrl,
        String feedbackWebhookUrl,
        Duration connectTimeout,
        Duration readTimeout
) {

    public SlackProperties {
        RequiredProperty.require("slack.report-webhook-url", reportWebhookUrl);
        RequiredProperty.require("slack.feedback-webhook-url", feedbackWebhookUrl);
        RequiredProperty.requirePositive("slack.connect-timeout", connectTimeout);
        RequiredProperty.requirePositive("slack.read-timeout", readTimeout);
    }
}
