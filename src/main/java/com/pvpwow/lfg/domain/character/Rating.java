package com.pvpwow.lfg.domain.character;

import com.pvpwow.lfg.domain.shared.DomainRuleViolationException;

/**
 * Arena rating for one bracket — the visible CR a player reports by hand.
 *
 * <p>Named {@code Rating}, not {@code Mmr}: what players know and report is
 * their current rating; MMR is Blizzard's hidden matchmaking value.
 *
 * <p>Validates only the IMPOSSIBLE: a negative rating cannot exist. The old
 * 0..3500 ceiling was a plausibility guess pretending to be an invariant —
 * cutoffs move, and the Blizzard API, the future source of observed
 * standings, reports whatever the game reports.
 */
public record Rating(int value) {

    public Rating {
        if (value < 0) {
            throw new DomainRuleViolationException(
                    "Rating must not be negative, got: %d".formatted(value));
        }
    }
}
