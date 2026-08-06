package com.nidus.twinly.auth.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "verification.test")
public record TestVerificationProperties(
        String code,
        String phonePrefix,
        String emailPrefix
) {

    public boolean matches(String contact) {
        if (code == null || contact == null) {
            return false;
        }

        return startsWith(phonePrefix, contact) || startsWith(emailPrefix, contact);
    }

    private boolean startsWith(String prefix, String contact) {
        return prefix != null && !prefix.isBlank() && contact.startsWith(prefix);
    }
}
