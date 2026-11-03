package com.pvpwow.lfg.domain.listing;

import com.pvpwow.lfg.domain.character.Bracket;
import com.pvpwow.lfg.domain.character.CharacterId;
import com.pvpwow.lfg.domain.character.Specialization;
import com.pvpwow.lfg.domain.listing.ListingId;

import java.time.Instant;
import java.util.Objects;

/**
 * What happened to a listing that will never be visible again.
 *
 * <p>This is the FACT, not the listing. The listing itself is deleted after
 * the retention window; the fact stays forever, because it is where the
 * metrics come from. Three kinds, and the difference matters:
 *
 * <ul>
 *   <li>{@code COMPLETED} — a group formed. Both sides agreed.</li>
 *   <li>{@code CANCELLED} — the owner pulled it, by hand.</li>
 *   <li>{@code EXPIRED} — it ran out of time.</li>
 * </ul>
 *
 * <p>The success rate is CONSERVATIVE: it counts only the groups formed
 * inside the app, so it under-reports. That is deliberate — a rate that
 * counted groups formed elsewhere could not be verified and could be
 * inflated. It will be revisited with real data.
 *
 * <p>{@code characterId} may be null: the fact survives the deletion of the
 * character it belongs to.
 */
public record ListingOutcome(
        ListingId listingId,
        CharacterId characterId,
        Bracket bracket,
        Specialization spec,
        ListingOutcomeKind kind,
        Instant createdAt,
        Instant closedAt) {

    public ListingOutcome {
        Objects.requireNonNull(listingId, "listingId must not be null");
        Objects.requireNonNull(bracket, "bracket must not be null");
        Objects.requireNonNull(spec, "spec must not be null");
        Objects.requireNonNull(kind, "kind must not be null");
        Objects.requireNonNull(createdAt, "createdAt must not be null");
        Objects.requireNonNull(closedAt, "closedAt must not be null");
    }
}
