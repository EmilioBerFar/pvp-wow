package com.pvpwow.lfg.domain.character;

import com.pvpwow.lfg.domain.shared.DomainRuleViolationException;

/**
 * Arena rating for one bracket — the visible CR a player reports by hand.
 *
 * <p>Named {@code Rating}, not {@code Mmr}: what players know and report is
 * their current rating; MMR is Blizzard's hidden matchmaking value.
 */
public record Rating(int value) {

    private static final int MIN = 0;
    private static final int MAX = 3500;

    public Rating {
        if (value < MIN || value > MAX) {
            throw new DomainRuleViolationException(
                    "Rating must be %d-%d, got: %d".formatted(MIN, MAX, value));
        }
    }
}
