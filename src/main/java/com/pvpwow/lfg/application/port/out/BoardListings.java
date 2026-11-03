package com.pvpwow.lfg.application.port.out;

import com.pvpwow.lfg.domain.character.Bracket;
import com.pvpwow.lfg.domain.character.Character;
import com.pvpwow.lfg.domain.character.ItemLevel;
import com.pvpwow.lfg.domain.character.PvpSeason;
import com.pvpwow.lfg.domain.character.Rating;
import com.pvpwow.lfg.domain.character.Region;
import com.pvpwow.lfg.domain.character.WowClass;
import com.pvpwow.lfg.domain.listing.LfgListing;
import com.pvpwow.lfg.domain.listing.ListingId;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

/**
 * Driven read port for the board. Filtering, ordering and paging belong in the
 * query adapter, where PostgreSQL can use indexes; loading every listing into
 * Java and filtering it here would defeat the relational schema chosen in WU9.
 *
 * <p>Paging is keyset, never offset: the cursor is the {@code (createdAt, id)}
 * pair of the last row the caller received, which is exactly the composite
 * order the board uses. An offset would skip or repeat rows as soon as a new
 * listing lands between two requests.
 */
public interface BoardListings {

    /**
     * Returns one page of ACTIVE, unexpired rows ordered by {@code createdAt}
     * descending, then {@code id} descending as the tie-break.
     *
     * <p>A minimum rating matches only current-season observations made at or
     * after {@code evidenceFreshAfter}; a minimum item level matches only gear
     * observed at or after the same instant. Candidates with old evidence
     * remain visible when no threshold was requested.
     *
     * <p>{@link BoardSlice#candidates()} never exceeds {@code criteria.limit()};
     * {@link BoardSlice#hasMore()} says whether another page exists.
     */
    BoardSlice search(SearchCriteria criteria);

    record SearchCriteria(
            Region region,
            Bracket bracket,
            WowClass wowClass,
            Rating minimumRating,
            ItemLevel minimumItemLevel,
            PvpSeason currentSeason,
            Instant evidenceFreshAfter,
            Instant now,
            BoardCursor cursor,
            int limit) {

        public SearchCriteria {
            Objects.requireNonNull(region, "region must not be null");
            Objects.requireNonNull(bracket, "bracket must not be null");
            Objects.requireNonNull(currentSeason, "currentSeason must not be null");
            Objects.requireNonNull(evidenceFreshAfter, "evidenceFreshAfter must not be null");
            Objects.requireNonNull(now, "now must not be null");
            if (limit < 1) {
                throw new IllegalArgumentException("limit must be positive, got: " + limit);
            }
        }
    }

    /**
     * Position of the last row of the previous page. A null cursor in
     * {@link SearchCriteria} means "start at the newest row".
     */
    record BoardCursor(Instant createdAt, ListingId listingId) {

        public BoardCursor {
            Objects.requireNonNull(createdAt, "createdAt must not be null");
            Objects.requireNonNull(listingId, "listingId must not be null");
        }
    }

    record BoardSlice(List<BoardCandidate> candidates, boolean hasMore) {

        public BoardSlice {
            Objects.requireNonNull(candidates, "candidates must not be null");
            candidates = List.copyOf(candidates);
        }
    }

    record BoardCandidate(LfgListing listing, Character character) {

        public BoardCandidate {
            Objects.requireNonNull(listing, "listing must not be null");
            Objects.requireNonNull(character, "character must not be null");
        }
    }
}
