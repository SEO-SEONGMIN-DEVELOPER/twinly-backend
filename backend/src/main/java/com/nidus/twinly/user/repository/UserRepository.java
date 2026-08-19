package com.nidus.twinly.user.repository;

import com.nidus.twinly.user.entity.User;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {

    boolean existsByNickname(String nickname);

    boolean existsByPhoneNumberHash(String phoneNumberHash);

    boolean existsByEmailHash(String emailHash);

    Optional<User> findByPhoneNumberHash(String phoneNumberHash);

    Optional<User> findByEmailHash(String emailHash);

    List<User> findAllByDeletedAtIsNullAndWithdrawalScheduledAtLessThanEqual(Instant now, Pageable pageable);

    int countByDeletedAtIsNull();

    int countByDeletedAtIsNullAndOrganizationHash(String organizationHash);

    @Query(value = """
            SELECT u.id
            FROM users u
            WHERE u.deleted_at IS NULL
              AND (:cursor IS NULL OR u.id > :cursor)
            ORDER BY u.id ASC
            LIMIT :limit
            """, nativeQuery = true)
    List<Long> findIdsAfterCursor(@Param("cursor") Long cursor,
                                  @Param("limit") int limit);
}