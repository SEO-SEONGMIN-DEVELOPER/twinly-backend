package com.nidus.twinly.invite.repository;

import com.nidus.twinly.invite.entity.InviteRegistration;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface InviteRegistrationRepository extends JpaRepository<InviteRegistration, Long> {

    Optional<InviteRegistration> findByInviteeUserId(Long inviteeUserId);

    boolean existsByInviteeUserId(Long inviteeUserId);

    int countByInviterUserId(Long inviterUserId);

    int countByInviterUserIdAndCompletedAtIsNotNull(Long inviterUserId);

    @Query(value = """
            SELECT r.inviter_user_id
            FROM invite_registrations r
            LEFT JOIN invite_rewards w ON w.user_id = r.inviter_user_id
            WHERE r.completed_at IS NOT NULL
              AND w.id IS NULL
            GROUP BY r.inviter_user_id
            HAVING COUNT(*) >= :goal
            """, nativeQuery = true)
    List<Long> findInviterIdsReachedWithoutReward(@Param("goal") int goal);
}
