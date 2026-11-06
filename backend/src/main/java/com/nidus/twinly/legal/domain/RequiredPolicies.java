package com.nidus.twinly.legal.domain;

import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public record RequiredPolicies(
        List<Set<Long>> acceptedPolicyIdGroups
) {

    public Set<Long> acceptedPolicyIds() {
        return acceptedPolicyIdGroups.stream()
                .flatMap(Set::stream)
                .collect(Collectors.toSet());
    }

    public int count() {
        return acceptedPolicyIdGroups.size();
    }

    public boolean isSatisfiedBy(Collection<Long> agreedPolicyIds) {
        return acceptedPolicyIdGroups.stream()
                .allMatch(group -> agreedPolicyIds.stream().anyMatch(group::contains));
    }
}
