package com.pvpwow.lfg.adapters.out.persistence;

import com.pvpwow.lfg.domain.character.Bracket;
import com.pvpwow.lfg.domain.listing.ListingStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.UUID;

interface ListingJpaRepository extends JpaRepository<ListingEntity, UUID> {

    /**
     * Marks the character's due ACTIVE listings EXPIRED before a new one is
     * inserted. PostgreSQL cannot use {@code now()} inside the partial unique
     * index predicate, so this sweep is what keeps an overdue-but-ACTIVE row
     * from blocking the slot forever.
     */
    @Modifying
    @Query("""
            update ListingEntity l
               set l.status = :expired
             where l.character.id = :characterId
               and l.bracket = :bracket
               and l.status = :active
               and l.expiresAt <= :now
            """)
    int expireDue(@Param("characterId") UUID characterId,
                  @Param("bracket") Bracket bracket,
                  @Param("active") ListingStatus active,
                  @Param("expired") ListingStatus expired,
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
