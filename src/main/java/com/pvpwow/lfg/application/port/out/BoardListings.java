package com.pvpwow.lfg.application.port.out;

import com.pvpwow.lfg.domain.character.Bracket;
import com.pvpwow.lfg.domain.character.Character;
import com.pvpwow.lfg.domain.character.ItemLevel;
import com.pvpwow.lfg.domain.character.PvpSeason;
import com.pvpwow.lfg.domain.character.Rating;
import com.pvpwow.lfg.domain.character.Region;
import com.pvpwow.lfg.domain.character.WowClass;
import com.pvpwow.lfg.domain.listing.LfgListing;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

/**
 * Driven read port for the board. Filtering and ordering belong in the query
 * adapter, where PostgreSQL can use indexes; loading every listing into Java
 * and filtering it here would defeat the relational schema chosen in WU9.
 */
public interface BoardListings {

    /**
     * Returns ACTIVE, unexpired rows ordered by {@code createdAt} descending.
     * A minimum rating matches only current-season observations made at or
     * after {@code evidenceFreshAfter}; a minimum item level matches only gear
     * observed at or after the same instant. Candidates with old evidence
     * remain visible when no threshold was requested.
     */
    List<BoardCandidate> search(SearchCriteria criteria);

    record SearchCriteria(
            Region region,
            Bracket bracket,
            WowClass wowClass,
            Rating minimumRating,
            ItemLevel minimumItemLevel,
            PvpSeason currentSeason,
            Instant evidenceFreshAfter,
            Instant now) {

        public SearchCriteria {
            Objects.requireNonNull(region, "region must not be null");
            Objects.requireNonNull(bracket, "bracket must not be null");
            Objects.requireNonNull(currentSeason, "currentSeason must not be null");
            Objects.requireNonNull(evidenceFreshAfter, "evidenceFreshAfter must not be null");
            Objects.requireNonNull(now, "now must not be null");
        }
    }

    record BoardCandidate(LfgListing listing, Character character) {

        public BoardCandidate {
            Objects.requireNonNull(listing, "listing must not be null");
            Objects.requireNonNull(character, "character must not be null");
        }
    }
}
