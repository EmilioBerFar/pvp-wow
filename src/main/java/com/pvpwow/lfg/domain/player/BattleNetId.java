package com.pvpwow.lfg.domain.player;

import com.pvpwow.lfg.domain.shared.DomainRuleViolationException;

import java.util.Objects;

/**
 * The Battle.net account id: the {@code sub} claim of the provider's userinfo
 * response.
 *
 * <p>This, not the battle tag, is what identifies an account. A battle tag is
 * a display and contact value that Battle.net lets a player rename; the
 * account id never changes, so a rename must not create a second player.
 *
 * <p>Typed as a {@link String} because that is what the provider sends: the
 * OpenID Connect specification defines {@code sub} as a string, and a numeric
 * id is only a convention of today's Battle.net, not a promise. Storing the
 * claim verbatim means a provider that changes the format does not break the
 * accounts already stored.
 */
public record BattleNetId(String value) {

    public BattleNetId {
        Objects.requireNonNull(value, "battle.net account id must not be null");
        String candidate = value.trim();
        if (candidate.isEmpty()) {
            throw new DomainRuleViolationException(
                    "Battle.net account id must not be blank");
        }
        value = candidate;
    }

    /**
     * Reads the claim as the provider writes it: a non-empty string.
     */
    public static BattleNetId parse(String raw) {
        Objects.requireNonNull(raw, "battle.net account id must not be null");
        return new BattleNetId(raw);
    }
}
