package com.nidus.twinly.admin.service;

import com.nidus.twinly.admin.domain.MemberStatus;
import com.nidus.twinly.admin.dto.command.AdminMemberSearchCommand;
import com.nidus.twinly.admin.dto.result.AdminMemberDetailResult;
import com.nidus.twinly.admin.dto.result.AdminMemberListItemResult;
import com.nidus.twinly.admin.dto.result.AdminPageResult;
import com.nidus.twinly.admin.dto.result.AdminSceneResult;
import com.nidus.twinly.admin.dto.result.AdminSubscriptionResult;
import com.nidus.twinly.admin.reader.AdminMasking;
import com.nidus.twinly.admin.reader.AdminMemberDetailReader;
import com.nidus.twinly.admin.reader.AdminMemberReader;
import com.nidus.twinly.admin.reader.AdminSceneReader;
import com.nidus.twinly.admin.reader.AdminSubscriptionReader;
import com.nidus.twinly.common.domain.VerificationType;
import com.nidus.twinly.common.web.BusinessException;
import com.nidus.twinly.common.web.ErrorCode;
import com.nidus.twinly.organization.entity.Organization;
import com.nidus.twinly.organization.repository.OrganizationRepository;
import com.nidus.twinly.user.entity.User;
import com.nidus.twinly.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class AdminMemberService {

    private static final int ORGANIZATION_SUGGESTION_LIMIT = 20;

    private final AdminMemberReader adminMemberReader;
    private final AdminMemberDetailReader adminMemberDetailReader;
    private final AdminSubscriptionReader adminSubscriptionReader;
    private final AdminSceneReader adminSceneReader;
    private final UserRepository userRepository;
    private final OrganizationRepository organizationRepository;

    @Transactional(readOnly = true)
    public AdminPageResult<AdminMemberListItemResult> search(AdminMemberSearchCommand command) {
        AdminPageResult<User> users = adminMemberReader.search(command);
        Map<Long, AdminSubscriptionResult> subscriptionByUserId = adminSubscriptionReader.byUserIds(
                users.items().stream().map(User::getId).toList());

        return users.map(user -> new AdminMemberListItemResult(
                user.getId(),
                user.getNickname(),
                user.getGender(),
                AdminMasking.email(user.getEmail()),
                user.getOrganization(),
                user.getCreatedAt(),
                MemberStatus.of(user),
                subscriptionByUserId.get(user.getId())
        ));
    }

    @Transactional(readOnly = true)
    public AdminMemberDetailResult detail(Long userId) {
        User user = userRepository.findById(userId)
                .filter(found -> !found.isSeed())
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
        Map<VerificationType, Instant> verifiedAt = adminMemberDetailReader.verifiedAtByType(userId);

        return new AdminMemberDetailResult(
                user.getId(),
                user.getNickname(),
                user.getGender(),
                AdminMasking.email(user.getEmail()),
                user.getOrganization(),
                user.getAffiliation(),
                user.getCreatedAt(),
                MemberStatus.of(user),
                user.getWithdrawalRequestedAt(),
                user.getDeletedAt(),
                verifiedAt.get(VerificationType.IDENTITY),
                verifiedAt.get(VerificationType.EMAIL),
                adminSubscriptionReader.byUserIds(List.of(userId)).get(userId),
                adminMemberDetailReader.sceneDates(userId),
                adminMemberDetailReader.relatedCases(userId)
        );
    }

    @Transactional(readOnly = true)
    public List<AdminSceneResult> scenes(Long userId, LocalDate date) {
        if (!userRepository.existsById(userId)) {
            throw new BusinessException(ErrorCode.USER_NOT_FOUND);
        }

        return adminSceneReader.scenes(userId, date);
    }

    @Transactional(readOnly = true)
    public List<String> organizationNames(String query) {
        String keyword = query == null ? "" : query.trim();

        return organizationRepository.findAllByOrderByNameAsc().stream()
                .map(Organization::getName)
                .filter(name -> !StringUtils.hasText(keyword) || name.contains(keyword))
                .limit(ORGANIZATION_SUGGESTION_LIMIT)
                .toList();
    }
}
