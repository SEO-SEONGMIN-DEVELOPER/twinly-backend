package com.nidus.twinly.organization.event;

public record CompanyRegisteredEvent(
        Long organizationId,
        String name,
        String domain
) {
}
