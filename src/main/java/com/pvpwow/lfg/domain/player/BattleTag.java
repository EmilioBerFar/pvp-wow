package com.pvpwow.lfg.domain.player;

import com.pvpwow.lfg.domain.shared.DomainRuleViolationException;

import java.util.Locale;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Battle.net contact identity ({@code name#1234}).
 *
 * <p>Account-level, never per character: one battle tag reaches every toon the
 * player owns. It is the ONLY contact channel in the product, which is why it
 * is required. Battle.net OAuth later enriches this concept, never replaces
 * it.
 *
 * <p>The name part is lowercased because Battle.net treats it
 * case-insensitively: {@code Emilio#12345} and {@code emilio#12345} are the
 * same account, so they must be the same value object.
 */
public record BattleTag(String value) {

    private static final Pattern PATTERN = Pattern.compile("[a-z0-9]{3,12}#\\d{4,5}");

    public BattleTag {
        Objects.requireNonNull(value, "battle tag must not be null");
        String candidate = value.trim().toLowerCase(Locale.ROOT);
        if (!PATTERN.matcher(candidate).matches()) {
            throw new DomainRuleViolationException(
                    "Battle tag must look like name#1234 (3-12 alphanumerics, 4-5 digits), got: '%s'"
                            .formatted(candidate));
        }
        value = candidate;
    }
}
