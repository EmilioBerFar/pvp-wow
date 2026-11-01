package com.pvpwow.lfg.domain.character;

import com.pvpwow.lfg.domain.shared.DomainRuleViolationException;

/**
 * Character gear item level. Gear determines arena readiness, so it is
 * required on every character.
 *
 * <p>The range (1-1000) is deliberately loose: ilvl caps change every season
 * and expansion, and tight validation rots.
 */
public record ItemLevel(int value) {

    private static final int MIN = 1;
    private static final int MAX = 1000;

    public ItemLevel {
        if (value < MIN || value > MAX) {
            throw new DomainRuleViolationException(
                    "Item level must be %d-%d, got: %d".formatted(MIN, MAX, value));
        }
    }
}
