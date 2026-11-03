package com.pvpwow.lfg.adapters.out.persistence;

import com.pvpwow.lfg.domain.join.JoinRequestStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

interface JoinRequestJpaRepository extends JpaRepository<JoinRequestEntity, UUID> {

    /**
     * The pending requests of a player that are still ALIVE, for the "how
     * many are you holding" rule. The partial index
     * {@code ix_join_requests_pending_per_player} is what makes this cheap.
     *
     * <p>The expiration matters: a request whose listing died is expired by
     * definition, even before the retention job marks it. Counting it would
     * block the player for good.
     */
    @Query("""
            select count(r)
              from JoinRequestEntity r
             where r.ownerId = :ownerId
               and r.status = :pending
               and r.expiresAt > :now
            """)
    int countPending(@Param("ownerId") UUID ownerId,
                     @Param("pending") JoinRequestStatus pending,
                     @Param("now") Instant now);

    /**
     * Whether the player already asked to join this listing, with ANY of
     * their characters. The unique index
     * {@code ux_join_requests_pending_per_player_listing} is the law; this
     * is the friendly pre-check.
     */
    @Query("""
            select count(r) > 0
              from JoinRequestEntity r
             where r.ownerId = :ownerId
               and r.listingId = :listingId
               and r.status = :pending
            """)
    boolean existsPending(@Param("ownerId") UUID ownerId,
                           @Param("listingId") UUID listingId,
                           @Param("pending") JoinRequestStatus pending);

    /**
     * Cancels EVERY pending request of a player. Used when the player starts
     * playing: everything they were waiting for is now irrelevant, whichever
     * character sent it.
     */
    @Modifying
    @Query("""
            update JoinRequestEntity r
               set r.status = :cancelled
             where r.ownerId = :ownerId
               and r.status = :pending
            """)
    int cancelPending(@Param("ownerId") UUID ownerId,
                       @Param("pending") JoinRequestStatus pending,
                       @Param("cancelled") JoinRequestStatus cancelled);

    /**
     * Cancels the pending requests aimed at a listing that no longer exists.
     * The listing is gone, so the requests waiting for an answer to it are
     * gone too — and they must not keep occupying a player's slots.
     */
    @Modifying
    @Query("""
            update JoinRequestEntity r
               set r.status = :cancelled
             where r.listingId = :listingId
               and r.status = :pending
            """)
    int cancelPendingForListing(@Param("listingId") UUID listingId,
                                 @Param("pending") JoinRequestStatus pending,
                                 @Param("cancelled") JoinRequestStatus cancelled);

    /**
     * Marks the pending requests that outlived their listing as EXPIRED. Only
     * the retention job runs it: it is the clock that decides.
     */
    @Modifying
    @Query("""
            update JoinRequestEntity r
               set r.status = :expired
             where r.status = :pending
               and r.expiresAt <= :now
            """)
    int expirePending(@Param("pending") JoinRequestStatus pending,
                      @Param("expired") JoinRequestStatus expired,
                      @Param("now") Instant now);

    /**
     * Writes the status of a request that already exists. Spring Data's
     * save() would issue a MERGE for an entity with an assigned id, so the
     * UPDATE is written explicitly.
     */
    @Modifying
    @Query("""
            update JoinRequestEntity r
               set r.status = :status
             where r.id = :id
            """)
    int updateStatus(@Param("id") UUID id, @Param("status") JoinRequestStatus status);

    List<JoinRequestEntity> findByIdIn(List<UUID> ids);
}
