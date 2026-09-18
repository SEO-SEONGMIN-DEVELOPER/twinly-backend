package com.nidus.twinly.common.fcm;

import com.nidus.twinly.common.config.RequiredProperty;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "fcm")
public record FcmProperties(
        String serviceAccountBase64
) {

    public FcmProperties {
        RequiredProperty.require("fcm.service-account-base64", serviceAccountBase64);
    }
}
