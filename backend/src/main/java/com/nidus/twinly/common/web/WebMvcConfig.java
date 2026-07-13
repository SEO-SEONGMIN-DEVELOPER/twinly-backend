package com.nidus.twinly.common.web;

import com.nidus.twinly.anon.resolver.CurrentAnonSessionArgumentResolver;
import com.nidus.twinly.user.resolver.CurrentUserArgumentResolver;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.List;

@Configuration
@RequiredArgsConstructor
public class WebMvcConfig implements WebMvcConfigurer {

    private final CurrentAnonSessionArgumentResolver anonSessionArgumentResolver;
    private final CurrentUserArgumentResolver currentUserArgumentResolver;

    @Override
    public void addArgumentResolvers(List<HandlerMethodArgumentResolver> resolvers) {
        resolvers.add(anonSessionArgumentResolver);
        resolvers.add(currentUserArgumentResolver);
    }
}
