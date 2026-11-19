package com.pvpwow.lfg.domain.character;

import com.pvpwow.lfg.domain.shared.DomainRuleViolationException;

import java.util.Objects;

/**
 * The id Blizzard knows a character by.
 *
 * <p>WHY THIS IS IDENTITY AND NOT PROVENANCE: a character's natural key -
 * region, realm and name - is NOT stable. Paid renames exist, and realms
 * move. So a character that changes its name is still the SAME character, and
 * the only thing that says so is the id the provider gave it. It is also what
 * makes a re-import recognise a character instead of duplicating it: the
 * import asks "is this the character Blizzard calls 161996209?", and the
 * answer does not depend on what the character is called today.
 *
 * <p>WHY IT IS REQUIRED: every character in this product comes from the
 * import. A character registered by hand - with a rating nobody verified, on
 * a board whose whole value is that its ratings can be trusted - is not a
 * character this product has. So there is no such thing as a character
 * without a Blizzard id, and the type does not model one.
 *
 * <p>WHY A NUMBER: Blizzard reports character ids as JSON numbers, and they
 * are large but well within a long. Unlike the Battle.net ACCOUNT id - which
 * the OpenID Connect specification defines as a string, and which is stored
 * verbatim for that reason - a character id has no such specification, and
 * the provider sends a number.
 */
public record BlizzardCharacterId(long value) {

    public BlizzardCharacterId {
        if (value <= 0) {
            throw new DomainRuleViolationException(
                    "Blizzard character id must be positive, got: %d".formatted(value));
        }
    }

    /**
     * Reads the id as the provider sends it.
     */
    public static BlizzardCharacterId of(long raw) {
        return new BlizzardCharacterId(raw);
    }
}
