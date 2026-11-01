package com.pvpwow.lfg.domain.synergy;

import com.pvpwow.lfg.domain.shared.DomainRuleViolationException;

/**
 * How well two classes play together in an arena comp, from 0 to 100.
 *
 * <p>The scale is relative, not a promise: what matters is the ORDER between
 * pairs, because the board sorts candidates by it.
 */
public record SynergyScore(int value) {

    private static final int MIN = 0;
    private static final int MAX = 100;

    public SynergyScore {
        if (value < MIN || value > MAX) {
            throw new DomainRuleViolationException(
                    "Synergy score must be %d-%d, got: %d".formatted(MIN, MAX, value));
        }
    }
}
