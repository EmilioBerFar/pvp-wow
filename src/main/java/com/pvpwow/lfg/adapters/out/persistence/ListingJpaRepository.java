package com.pvpwow.lfg.adapters.out.persistence;

import com.pvpwow.lfg.domain.character.Bracket;
import com.pvpwow.lfg.domain.listing.ListingStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
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
}
