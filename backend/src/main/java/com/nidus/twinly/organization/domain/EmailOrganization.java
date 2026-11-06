package com.nidus.twinly.organization.domain;

import com.nidus.twinly.organization.entity.Organization;

public record EmailOrganization(
        String domain,
        OrganizationType type,
        Organization organization
) {

    public static EmailOrganization registered(String domain, Organization organization) {
        return new EmailOrganization(domain, organization.getType(), organization);
    }

    public static EmailOrganization unregisteredCompany(String domain) {
        return new EmailOrganization(domain, OrganizationType.COMPANY, null);
    }

    public boolean isUnregisteredCompany() {
        return organization == null;
    }
}
