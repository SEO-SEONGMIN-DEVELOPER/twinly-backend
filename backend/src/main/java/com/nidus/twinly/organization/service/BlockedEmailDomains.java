package com.nidus.twinly.organization.service;

import jakarta.annotation.PostConstruct;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

@Component
public class BlockedEmailDomains {

    private static final List<String> RESOURCE_PATHS = List.of("email/personal-domains.txt", "email/disposable-domains.txt");
    private static final List<String> HIGHER_EDUCATION_SUFFIXES = List.of(".ac.kr", ".edu");
    private static final List<String> HIGHER_EDUCATION_LABELS = List.of(".ac.", ".edu.");

    private Set<String> domains;

    @PostConstruct
    public void load() throws IOException {
        Set<String> loaded = new HashSet<>();

        for (String path : RESOURCE_PATHS) {
            String content = new ClassPathResource(path).getContentAsString(StandardCharsets.UTF_8);
            content.lines()
                    .map(line -> line.trim().toLowerCase(Locale.ROOT))
                    .filter(line -> !line.isEmpty())
                    .forEach(loaded::add);
        }

        if (loaded.isEmpty()) {
            throw new IllegalStateException("차단할 이메일 도메인 목록이 비어 있습니다: " + RESOURCE_PATHS);
        }

        domains = Set.copyOf(loaded);
    }

    public boolean isBlocked(String domain) {
        return domains.contains(domain) || isHigherEducation(domain);
    }

    private boolean isHigherEducation(String domain) {
        return HIGHER_EDUCATION_SUFFIXES.stream().anyMatch(domain::endsWith)
                || HIGHER_EDUCATION_LABELS.stream().anyMatch(domain::contains);
    }
}
