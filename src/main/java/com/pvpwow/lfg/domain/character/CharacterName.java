package com.pvpwow.lfg.domain.character;

import com.pvpwow.lfg.domain.shared.DomainRuleViolationException;

import java.util.Objects;

/**
 * World of Warcraft character name.
 *
 * <p>Rules (mirroring Blizzard's naming restrictions): 2 to 12 Unicode letters,
 * no digits, no spaces, no symbols. Accented letters are valid on EU realms.
 *
 * <p>This is the reference standard for every other value object in this
 * domain: a Java record whose compact constructor validates its own rules,
 * so an invalid value simply cannot exist.
 */
public record CharacterName(String value) {

    private static final int MIN_LENGTH = 2;
    private static final int MAX_LENGTH = 12;

    public CharacterName {
        Objects.requireNonNull(value, "name must not be null");
        String candidate = value.trim();
        if (candidate.length() < MIN_LENGTH
                || candidate.length() > MAX_LENGTH
                || !candidate.chars().allMatch(Character::isLetter)) {
            throw new DomainRuleViolationException(
                    "Character name must be 2-12 letters, got: '%s'".formatted(candidate));
        }
        value = candidate;
    }
}
