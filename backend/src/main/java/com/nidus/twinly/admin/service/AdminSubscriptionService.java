package com.nidus.twinly.admin.service;

import com.nidus.twinly.admin.domain.MemberStatus;
import com.nidus.twinly.admin.domain.SubscriptionKind;
import com.nidus.twinly.admin.dto.result.AdminMemberSummaryResult;
import com.nidus.twinly.admin.dto.result.AdminPageResult;
import com.nidus.twinly.admin.dto.result.AdminSubscriptionListResult;
import com.nidus.twinly.admin.dto.result.AdminSubscriptionResult;
import com.nidus.twinly.admin.reader.AdminMemberReader;
import com.nidus.twinly.admin.reader.AdminSubscriptionReader;
import com.nidus.twinly.user.entity.User;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

@Service
@RequiredArgsConstructor
public class AdminSubscriptionService {

    private final AdminSubscriptionReader adminSubscriptionReader;
    private final AdminMemberReader adminMemberReader;

    @Transactional(readOnly = true)
    public AdminSubscriptionListResult list(SubscriptionKind kind, boolean activeOnly, int page, int size) {
        AdminPageResult<AdminSubscriptionResult> subscriptions = adminSubscriptionReader.page(kind, activeOnly, page, size);
        Map<Long, User> userById = adminMemberReader.usersById(subscriptions.items().stream().map(AdminSubscriptionResult::userId).toList());

        return new AdminSubscriptionListResult(
                adminSubscriptionReader.activeCountsByKind(),
                subscriptions.map(subscription -> new AdminSubscriptionListResult.Item(
                        summary(userById.get(subscription.userId())), subscription))
        );
    }

    private AdminMemberSummaryResult summary(User user) {
        if (user == null) {
            return null;
        }

        return new AdminMemberSummaryResult(user.getId(), user.getNickname(), user.getGender(), user.getOrganization(), MemberStatus.of(user));
    }
}
