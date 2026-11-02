package com.pvpwow.lfg.application.port.in;

import com.pvpwow.lfg.domain.character.Bracket;
import com.pvpwow.lfg.domain.character.Character;
import com.pvpwow.lfg.domain.character.ItemLevel;
import com.pvpwow.lfg.domain.character.Rating;
import com.pvpwow.lfg.domain.character.Region;
import com.pvpwow.lfg.domain.character.WowClass;
import com.pvpwow.lfg.domain.listing.LfgListing;

import java.util.List;
import java.util.Objects;

/** Driving port: read the active LFG board for one region and bracket. */
public interface SearchBoard {

    List<BoardEntry> search(SearchBoardQuery query);

    /**
     * Region and bracket are mandatory because rated PvP and its ratings are
     * scoped by both. Null filters mean "do not filter by this field".
     */
    record SearchBoardQuery(
            Region region,
            Bracket bracket,
            WowClass wowClass,
            Rating minimumRating,
            ItemLevel minimumItemLevel) {

        public SearchBoardQuery {
            Objects.requireNonNull(region, "region must not be null");
            Objects.requireNonNull(bracket, "bracket must not be null");
        }
    }

    /**
     * A board row keeps the aggregate evidence intact: the UI can show the
     * pinned spec, availability, gear date and rating date instead of receiving
     * a bag of preformatted strings. Staleness is a read-time fact, so it lives
     * here and is never written back to the character.
     */
    record BoardEntry(LfgListing listing, Character character, boolean ratingStale) {

        public BoardEntry {
            Objects.requireNonNull(listing, "listing must not be null");
            Objects.requireNonNull(character, "character must not be null");
        }
    }
}
