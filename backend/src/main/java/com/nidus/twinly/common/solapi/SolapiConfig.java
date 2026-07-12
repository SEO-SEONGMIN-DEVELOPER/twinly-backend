package com.nidus.twinly.common.solapi;

import com.solapi.sdk.SolapiClient;
import com.solapi.sdk.message.service.DefaultMessageService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class SolapiConfig {

    @Bean
    public DefaultMessageService defaultMessageService(SolapiProperties solapiProperties) {
        return SolapiClient.INSTANCE.createInstance(solapiProperties.apiKey(), solapiProperties.apiSecretKey());
    }
}