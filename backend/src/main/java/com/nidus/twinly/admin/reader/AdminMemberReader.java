package com.nidus.twinly.admin.reader;

import com.nidus.twinly.admin.domain.MemberSearchKey;
import com.nidus.twinly.admin.domain.MemberStatus;
import com.nidus.twinly.admin.dto.command.AdminMemberSearchCommand;
import com.nidus.twinly.admin.dto.result.AdminPageResult;
import com.nidus.twinly.common.crypto.BlindIndexHasher;
import com.nidus.twinly.user.entity.User;
import com.nidus.twinly.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class AdminMemberReader {

    private final NamedParameterJdbcTemplate jdbcTemplate;
    private final UserRepository userRepository;
    private final BlindIndexHasher blindIndexHasher;

    public AdminPageResult<User> search(AdminMemberSearchCommand command) {
        Optional<MapSqlParameterSource> params = conditionParams(command);

        if (params.isEmpty()) {
            return new AdminPageResult<>(List.of(), command.page(), command.size(), 0);
        }

        String where = where(command);
        MapSqlParameterSource pageParams = params.get()
                .addValue("size", command.size())
                .addValue("offset", command.page() * command.size());

        List<Long> ids = jdbcTemplate.queryForList(
                "SELECT u.id FROM users u WHERE " + where + " ORDER BY " + command.sort().orderBy() + " LIMIT :size OFFSET :offset",
                pageParams, Long.class);
        Long total = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM users u WHERE " + where, pageParams, Long.class);

        return new AdminPageResult<>(usersInOrder(ids), command.page(), command.size(), total == null ? 0 : total);
    }

    public List<User> usersInOrder(Collection<Long> ids) {
        if (ids.isEmpty()) {
            return List.of();
        }

        Map<Long, User> userById = userRepository.findAllById(ids).stream()
                .collect(Collectors.toMap(User::getId, Function.identity()));

        return ids.stream()
                .map(userById::get)
                .filter(user -> user != null)
                .toList();
    }

    public Map<Long, User> usersById(Collection<Long> ids) {
        if (ids.isEmpty()) {
            return Map.of();
        }

        return userRepository.findAllById(ids).stream()
                .collect(Collectors.toMap(User::getId, Function.identity()));
    }

    private Optional<MapSqlParameterSource> conditionParams(AdminMemberSearchCommand command) {
        MapSqlParameterSource params = new MapSqlParameterSource();
        String query = command.query() == null ? null : command.query().trim();

        if (!StringUtils.hasText(query) || command.key() == null) {
            return Optional.of(params);
        }

        return switch (command.key()) {
            case ID -> parseId(query).map(id -> params.addValue("id", id));
            case NICKNAME -> Optional.of(params.addValue("nickname", "%" + escapeLike(query) + "%"));
            case EMAIL -> Optional.of(params.addValue("emailHashes", List.of(
                    blindIndexHasher.hash(query), blindIndexHasher.hash(query.toLowerCase(Locale.ROOT)))));
            case SCHOOL -> Optional.of(params.addValue("organizationHash", blindIndexHasher.hash(query)));
        };
    }

    private String where(AdminMemberSearchCommand command) {
        List<String> conditions = new ArrayList<>();
        conditions.add("u.is_seed = FALSE");

        if (command.status() != null) {
            conditions.add(statusCondition(command.status()));
        }

        if (StringUtils.hasText(command.query()) && command.key() != null) {
            conditions.add(searchCondition(command.key()));
        }

        return String.join(" AND ", conditions);
    }

    private String statusCondition(MemberStatus status) {
        return switch (status) {
            case ACTIVE -> "u.deleted_at IS NULL AND u.withdrawal_requested_at IS NULL";
            case WITHDRAWING -> "u.deleted_at IS NULL AND u.withdrawal_requested_at IS NOT NULL";
            case WITHDRAWN -> "u.deleted_at IS NOT NULL";
        };
    }

    private String searchCondition(MemberSearchKey key) {
        return switch (key) {
            case ID -> "u.id = :id";
            case NICKNAME -> "u.nickname LIKE :nickname ESCAPE '!'";
            case EMAIL -> "u.email_hash IN (:emailHashes)";
            case SCHOOL -> "u.organization_hash = :organizationHash";
        };
    }

    private Optional<Long> parseId(String raw) {
        try {
            return Optional.of(Long.parseLong(raw));
        } catch (NumberFormatException e) {
            return Optional.empty();
        }
    }

    private String escapeLike(String raw) {
        return raw.replace("!", "!!").replace("%", "!%").replace("_", "!_");
    }
}
