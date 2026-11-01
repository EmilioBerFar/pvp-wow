package com.pvpwow.lfg.domain.character;

import com.pvpwow.lfg.domain.shared.DomainRuleViolationException;

/**
 * Identifier of a rated PvP season.
 *
 * <p>Not decoration: a rating means nothing without the season it belongs to,
 * because ratings reset every season. "1800" in season 3 and "1800" in season
 * 4 are two different facts about the same character.
 *
 * <p>The identifier is Blizzard's ({@code season.id} in the profile API), so it
 * survives a sync without translation.
 */
public record PvpSeason(int id) {

    public PvpSeason {
        if (id <= 0) {
            throw new DomainRuleViolationException(
                    "PvP season id must be positive, got: %d".formatted(id));
        }
    }
}
