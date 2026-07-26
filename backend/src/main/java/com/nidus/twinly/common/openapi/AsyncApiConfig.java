package com.nidus.twinly.common.openapi;

import io.github.springwolf.asyncapi.v3.model.channel.message.MessageObject;
import io.github.springwolf.asyncapi.v3.model.channel.message.MessagePayload;
import io.github.springwolf.core.asyncapi.AsyncApiCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class AsyncApiConfig {

    private static final String ASYNCAPI_VERSION = "3.0.0";

    @Bean
    public AsyncApiCustomizer asyncApiVersionDowngradeCustomizer() {
        return asyncApi -> {
            asyncApi.setAsyncapi(ASYNCAPI_VERSION);

            if (asyncApi.getComponents() == null || asyncApi.getComponents().getMessages() == null) {
                return;
            }
            asyncApi.getComponents().getMessages().values().stream()
                    .filter(message -> message instanceof MessageObject)
                    .map(message -> ((MessageObject) message).getPayload())
                    .filter(payload -> payload != null)
                    .map(MessagePayload::getMultiFormatSchema)
                    .filter(schema -> schema != null && schema.getSchemaFormat() != null)
                    .forEach(schema -> schema.setSchemaFormat(
                            schema.getSchemaFormat().replace("3.1.0", ASYNCAPI_VERSION)));
        };
    }
}
