package com.pvpwow.lfg.domain.character;

import com.pvpwow.lfg.domain.shared.DomainRuleViolationException;

/**
 * Character gear item level. Gear determines arena readiness, so it is
 * required on every character.
 *
 * <p>Validates only the IMPOSSIBLE: a non-positive item level cannot exist.
 * Plausibility ("is 900 believable this season?") is deliberately modeled
 * nowhere — caps move every season and expansion, and once the Blizzard API
 * arrives it is the source of truth for observed gear. The old 1..1000 range
 * was a plausibility guess pretending to be an invariant: it would silently
 * reject valid data the day a cap moves past it.
 */
public record ItemLevel(int value) {

    public ItemLevel {
        if (value <= 0) {
            throw new DomainRuleViolationException(
                    "Item level must be positive, got: %d".formatted(value));
        }
    }
}
