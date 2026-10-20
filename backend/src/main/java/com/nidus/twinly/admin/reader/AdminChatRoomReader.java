package com.nidus.twinly.admin.reader;

import com.nidus.twinly.admin.domain.ChatEntryState;
import com.nidus.twinly.admin.domain.MemberStatus;
import com.nidus.twinly.admin.dto.result.AdminChatMessagePageResult;
import com.nidus.twinly.admin.dto.result.AdminChatRoomResult;
import com.nidus.twinly.admin.dto.result.AdminPageResult;
import com.nidus.twinly.user.entity.User;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class AdminChatRoomReader {

    private static final String TEXT_TYPE = "TEXT";

    private static final String ROOMS = """
            SELECT r.id, r.created_at, r.common_point
            FROM chat_rooms r
            WHERE r.id IN (:roomIds)
            """;

    private static final String PARTICIPATIONS = """
            SELECT p.room_id, p.user_id, p.entry_agreed_at, p.left_at
            FROM chat_room_participations p
            WHERE p.room_id IN (:roomIds)
            ORDER BY p.room_id, p.id
            """;

    private static final String MESSAGE_STATS = """
            SELECT c.room_id, COUNT(*) AS cnt, MAX(c.id) AS last_id
            FROM chats c
            WHERE c.room_id IN (:roomIds)
            GROUP BY c.room_id
            """;

    private static final String SENT_AT_BY_IDS = """
            SELECT c.id, c.sent_at
            FROM chats c
            WHERE c.id IN (:ids)
            """;

    private static final String MESSAGES = """
            SELECT c.id, c.sender_user_id, c.type, c.message, c.sent_at
            FROM chats c
            WHERE c.room_id = :roomId
              AND (:beforeId IS NULL OR c.id < :beforeId)
            ORDER BY c.id DESC
            LIMIT :limit
            """;

    private final NamedParameterJdbcTemplate jdbcTemplate;
    private final AdminMemberReader adminMemberReader;

    public AdminPageResult<AdminChatRoomResult> search(String query, ChatEntryState entryState, int page, int size) {
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("size", size)
                .addValue("offset", page * size);
        String having = having(query, entryState, params);
        String grouped = """
                SELECT r.id
                FROM chat_rooms r
                JOIN chat_room_participations p ON p.room_id = r.id
                JOIN users u ON u.id = p.user_id
                GROUP BY r.id
                HAVING """ + " " + having;

        List<Long> roomIds = jdbcTemplate.queryForList(grouped + " ORDER BY r.id DESC LIMIT :size OFFSET :offset", params, Long.class);
        Long total = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM (" + grouped + ") t", params, Long.class);

        return new AdminPageResult<>(rooms(roomIds), page, size, total == null ? 0 : total);
    }

    public Optional<AdminChatRoomResult> room(Long roomId) {
        return rooms(List.of(roomId)).stream()
                .filter(room -> room.participants().stream().noneMatch(participant -> participant.status() == null))
                .findFirst();
    }

    public AdminChatMessagePageResult messages(Long roomId, Long beforeId, int size) {
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("roomId", roomId)
                .addValue("beforeId", beforeId)
                .addValue("limit", size + 1);

        List<AdminChatMessagePageResult.Message> fetched = jdbcTemplate.query(MESSAGES, params, (rs, rowNum) -> {
            String type = rs.getString("type");
            return new AdminChatMessagePageResult.Message(
                    rs.getLong("id"),
                    rs.getLong("sender_user_id"),
                    type,
                    TEXT_TYPE.equals(type) ? rs.getString("message") : null,
                    instant(rs.getTimestamp("sent_at")));
        });

        boolean hasMore = fetched.size() > size;

        return new AdminChatMessagePageResult(hasMore ? fetched.subList(0, size) : fetched, hasMore);
    }

    private String having(String query, ChatEntryState entryState, MapSqlParameterSource params) {
        List<String> conditions = new ArrayList<>();
        conditions.add("SUM(u.is_seed) = 0");

        if (entryState != null) {
            conditions.add(switch (entryState) {
                case BOTH -> "SUM(p.entry_agreed_at IS NOT NULL) >= 2";
                case ONE -> "SUM(p.entry_agreed_at IS NOT NULL) = 1";
                case NONE -> "SUM(p.entry_agreed_at IS NOT NULL) = 0";
            });
        }

        String keyword = query == null ? null : query.trim();

        if (StringUtils.hasText(keyword)) {
            Optional<Long> number = parseLong(keyword);

            if (number.isPresent()) {
                params.addValue("number", number.get());
                conditions.add("(r.id = :number OR SUM(u.id = :number) > 0)");
            } else {
                params.addValue("nickname", "%" + keyword.replace("!", "!!").replace("%", "!%").replace("_", "!_") + "%");
                conditions.add("SUM(u.nickname LIKE :nickname ESCAPE '!') > 0");
            }
        }

        return String.join(" AND ", conditions);
    }

    private List<AdminChatRoomResult> rooms(List<Long> roomIds) {
        if (roomIds.isEmpty()) {
            return List.of();
        }

        Map<String, Object> params = Map.of("roomIds", roomIds);
        Map<Long, RoomRow> roomById = new HashMap<>();
        jdbcTemplate.query(ROOMS, params, rs -> {
            roomById.put(rs.getLong("id"), new RoomRow(instant(rs.getTimestamp("created_at")), rs.getString("common_point")));
        });

        Map<Long, List<ParticipationRow>> participationsByRoomId = new LinkedHashMap<>();
        jdbcTemplate.query(PARTICIPATIONS, params, rs -> {
            participationsByRoomId.computeIfAbsent(rs.getLong("room_id"), id -> new ArrayList<>())
                    .add(new ParticipationRow(rs.getLong("user_id"), instant(rs.getTimestamp("entry_agreed_at")), instant(rs.getTimestamp("left_at"))));
        });

        Map<Long, long[]> statsByRoomId = new HashMap<>();
        jdbcTemplate.query(MESSAGE_STATS, params, rs -> {
            statsByRoomId.put(rs.getLong("room_id"), new long[]{rs.getLong("cnt"), rs.getLong("last_id")});
        });
        Map<Long, Instant> sentAtByMessageId = sentAt(statsByRoomId.values().stream().map(stat -> stat[1]).collect(Collectors.toSet()));

        Set<Long> userIds = participationsByRoomId.values().stream()
                .flatMap(List::stream)
                .map(ParticipationRow::userId)
                .collect(Collectors.toSet());
        Map<Long, User> userById = adminMemberReader.usersById(userIds);

        return roomIds.stream()
                .filter(roomById::containsKey)
                .map(roomId -> {
                    List<ParticipationRow> participations = participationsByRoomId.getOrDefault(roomId, List.of());
                    long[] stats = statsByRoomId.get(roomId);
                    RoomRow room = roomById.get(roomId);

                    return new AdminChatRoomResult(
                            roomId,
                            room.openedAt(),
                            room.commonPoint(),
                            ChatEntryState.of(participations.stream().filter(p -> p.enteredAt() != null).count()),
                            participations.stream().map(p -> participant(p, userById.get(p.userId()))).toList(),
                            stats == null ? 0 : stats[0],
                            stats == null ? null : sentAtByMessageId.get(stats[1])
                    );
                })
                .toList();
    }

    private AdminChatRoomResult.Participant participant(ParticipationRow participation, User user) {
        if (user == null) {
            return new AdminChatRoomResult.Participant(participation.userId(), null, null, null, null,
                    participation.enteredAt(), participation.leftAt());
        }

        return new AdminChatRoomResult.Participant(
                user.getId(),
                user.getNickname(),
                user.getGender(),
                user.getOrganization(),
                user.isSeed() ? null : MemberStatus.of(user),
                participation.enteredAt(),
                participation.leftAt()
        );
    }

    private Map<Long, Instant> sentAt(Set<Long> messageIds) {
        if (messageIds.isEmpty()) {
            return Map.of();
        }

        Map<Long, Instant> result = new HashMap<>();
        jdbcTemplate.query(SENT_AT_BY_IDS, Map.of("ids", messageIds), rs -> {
            result.put(rs.getLong("id"), instant(rs.getTimestamp("sent_at")));
        });

        return result;
    }

    private Optional<Long> parseLong(String raw) {
        try {
            return Optional.of(Long.parseLong(raw));
        } catch (NumberFormatException e) {
            return Optional.empty();
        }
    }

    private static Instant instant(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toInstant();
    }

    private record RoomRow(Instant openedAt, String commonPoint) {
    }

    private record ParticipationRow(Long userId, Instant enteredAt, Instant leftAt) {
    }
}
