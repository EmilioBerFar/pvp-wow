package com.pvpwow.lfg.domain.character;

import com.pvpwow.lfg.domain.shared.DomainRuleViolationException;

import java.util.Locale;
import java.util.Objects;

/**
 * World of Warcraft realm (server).
 *
 * <p>Unlike {@link CharacterName}, spaces are VALID here — realms like
 * "Los Errantes" exist — so they are normalized, not rejected.
 * Normalization: trim, collapse inner whitespace runs to one space, lowercase.
 * Two realms written differently by two users are the same realm.
 */
public record Realm(String value) {

    public Realm {
        Objects.requireNonNull(value, "realm must not be null");
        String candidate = value.trim()
                .replaceAll("\\s+", " ")
                .toLowerCase(Locale.ROOT);
        if (candidate.isEmpty()) {
            throw new DomainRuleViolationException(
                    "Realm must not be blank, got: '%s'".formatted(value));
        }
        value = candidate;
    }
}
