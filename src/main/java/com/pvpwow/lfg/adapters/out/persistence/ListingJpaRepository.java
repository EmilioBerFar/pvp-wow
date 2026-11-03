package com.pvpwow.lfg.adapters.out.persistence;

import com.pvpwow.lfg.domain.character.Bracket;
import com.pvpwow.lfg.domain.listing.ListingStatus;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

interface ListingJpaRepository extends JpaRepository<ListingEntity, UUID> {

    /**
     * The ACTIVE listings of a character, in every bracket. Used by the group
     * completion: accepting a join request closes EVERY active listing of both
     * players, not just the one the request pointed at.
     */
    List<ListingEntity> findByCharacterIdAndStatus(UUID characterId, ListingStatus status);

    /**
     * The ACTIVE listings of a character in one bracket that are already due.
     * The sweep that frees the slot for a new publication uses it: those
     * listings are closed THROUGH the shared closer, so they write their
     * fact and cancel their pending requests like any other close.
     *
     * <p>Written in JPQL with an explicit comparison, because the derived
     * name would read the expiration as "expiresAt LESS THAN" — the opposite
     * of what a due listing is.
     */
    @Query("""
            select l
              from ListingEntity l
             where l.character.id = :characterId
               and l.bracket = :bracket
               and l.status = :status
               and l.expiresAt <= :now
            """)
    List<ListingEntity> findDue(@Param("characterId") UUID characterId,
                                 @Param("bracket") Bracket bracket,
                                 @Param("status") ListingStatus status,
                                 @Param("now") Instant now);

    /**
     * The advisory duplicate check. "Active" here means ACTIVE and not yet due:
     * an overdue row is logically expired even before a sweep updates it.
     */
    @Query("""
            select count(l) > 0
              from ListingEntity l
             where l.character.id = :characterId
               and l.bracket = :bracket
               and l.status = :active
               and l.expiresAt > :now
            """)
    boolean existsActive(@Param("characterId") UUID characterId,
                         @Param("bracket") Bracket bracket,
                         @Param("active") ListingStatus active,
                         @Param("now") Instant now);

    /**
     * One page of overdue ACTIVE listings, oldest first. The retention sweep
     * closes them through the shared closer, so the page has to be small
     * enough to be a short transaction.
     *
     * <p>A derived name would read {ExpiresAt} as an equality, not as
     * {@code <=}: the range is written out.
     */
    @Query("""
            select l
              from ListingEntity l
             where l.status = :active
               and l.expiresAt <= :now
             order by l.expiresAt asc, l.id asc
            """)
    List<ListingEntity> findOverdue(@Param("active") ListingStatus active,
                                     @Param("now") Instant now,
                                     Pageable page);

    /**
     * One page of closed listings that are past the retention window. Every
     * status except ACTIVE is fair game: the metrics read the facts, never
     * these rows, so keeping them would only grow the board table. Only the
     * ids are selected; the sweep deletes them with one statement and never
     * hydrates the aggregates it is about to remove.
     */
    @Query("""
            select l.id
              from ListingEntity l
             where l.status <> :active
               and l.expiresAt < :cutoff
             order by l.expiresAt asc, l.id asc
            """)
    List<UUID> findDeletableIds(@Param("active") ListingStatus active,
                                 @Param("cutoff") Instant cutoff,
                                 Pageable page);

    /**
     * Deletes a page of listings. The database cascades to the join requests
     * and the schedule tables; the facts are untouched, because no foreign key
     * ties them to the listing.
     */
    @Modifying
    @Query("delete from ListingEntity l where l.id in :ids")
    int deleteByIdIn(@Param("ids") Collection<UUID> ids);
}
