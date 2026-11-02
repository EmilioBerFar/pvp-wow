package com.pvpwow.lfg.application.port.out;

import com.pvpwow.lfg.domain.character.Bracket;
import com.pvpwow.lfg.domain.character.CharacterId;
import com.pvpwow.lfg.domain.listing.LfgListing;

import java.time.Instant;

/** Driven port: listing persistence and listing-specific queries. */
public interface Listings {

    /**
     * Persists a newly published listing. The adapter owns the publication
     * law: in one transaction it expires due ACTIVE rows for the same
     * character and bracket, then inserts this one. The database's partial
     * unique index arbitrates concurrent publishers.
     */
    void publish(LfgListing listing);

    /**
     * Whether the character already has a listing that is ACTIVE and not due
     * at {@code now}. An ACTIVE row past its expiration is logically expired,
     * even before a cleanup job updates its status.
     *
     * <p>This is the advisory duplicate check. {@link #publish} backs it with
     * a database constraint because concurrent publishers can race past any
     * application-level query.
     */
    boolean hasActiveFor(CharacterId characterId, Bracket bracket, Instant now);
}
