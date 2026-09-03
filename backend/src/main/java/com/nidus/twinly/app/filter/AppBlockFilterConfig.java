package com.nidus.twinly.app.filter;

import com.nidus.twinly.app.store.AppBlockPolicyStore;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import tools.jackson.databind.json.JsonMapper;

import java.time.Clock;

@Configuration
public class AppBlockFilterConfig {

    static final int ORDER = Ordered.HIGHEST_PRECEDENCE + 1;

    @Bean
    public FilterRegistrationBean<AppBlockFilter> appBlockFilterRegistration(AppBlockPolicyStore appBlockPolicyStore,
                                                                             JsonMapper jsonMapper,
                                                                             Clock clock) {
        FilterRegistrationBean<AppBlockFilter> registration =
                new FilterRegistrationBean<>(new AppBlockFilter(appBlockPolicyStore, jsonMapper, clock));
        registration.setOrder(ORDER);

        return registration;
    }
}
