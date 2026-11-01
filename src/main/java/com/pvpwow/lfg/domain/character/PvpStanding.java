package com.pvpwow.lfg.domain.character;

import java.time.Instant;
import java.util.Objects;

/**
 * One OBSERVED fact about a character's arena standing: the rating, the season
 * it belongs to, and when it was observed.
 *
 * <p>This is the seam between what the player declares (which specializations
 * they are willing to play, their languages, their availability — nobody can
 * observe those) and what an external source reports (rating, item level,
 * realm, class). Observed facts are always stamped, because a rating without a
 * season and a date is a claim nobody can check.
 *
 * <p>Observed does not mean live: Blizzard's profile endpoints refresh when the
 * character logs out, so even a synced value can be hours old. The timestamp is
 * what lets the UI say "as of" instead of lying.
 */
public record PvpStanding(Rating rating, PvpSeason season, Instant observedAt) {

    public PvpStanding {
        Objects.requireNonNull(rating, "rating must not be null");
        Objects.requireNonNull(season, "season must not be null");
        Objects.requireNonNull(observedAt, "observedAt must not be null");
    }
}
