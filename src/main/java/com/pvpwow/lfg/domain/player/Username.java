package com.pvpwow.lfg.domain.player;

import com.pvpwow.lfg.domain.shared.DomainRuleViolationException;

import java.util.Objects;
import java.util.regex.Pattern;

/**
 * The login handle of a player account on the board.
 *
 * <p>Deliberately ASCII: this is a technical handle, not a display name. It
 * must start with a letter, then 2 to 23 letters, digits, dots, underscores
 * or hyphens.
 */
public record Username(String value) {

    private static final Pattern PATTERN = Pattern.compile("[A-Za-z][A-Za-z0-9._-]{2,23}");

    public Username {
        Objects.requireNonNull(value, "username must not be null");
        String candidate = value.trim();
        if (!PATTERN.matcher(candidate).matches()) {
            throw new DomainRuleViolationException(
                    "Username must be 3-24 chars, start with a letter and use only letters, digits, '.', '_' or '-', got: '%s'"
                            .formatted(candidate));
        }
        value = candidate;
    }
}
