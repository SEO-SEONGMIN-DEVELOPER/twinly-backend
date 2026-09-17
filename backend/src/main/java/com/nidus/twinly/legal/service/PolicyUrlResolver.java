package com.nidus.twinly.legal.service;

import com.nidus.twinly.legal.config.LegalProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
@RequiredArgsConstructor
public class PolicyUrlResolver {

    private final LegalProperties legalProperties;

    public String resolve(String identifier) {
        String baseUrl = StringUtils.trimTrailingCharacter(legalProperties.baseUrl(), '/');
        return "%s/legal/%s/".formatted(baseUrl, identifier);
    }
}
