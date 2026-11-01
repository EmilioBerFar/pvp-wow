package com.pvpwow.lfg.application.port.in;

import com.pvpwow.lfg.domain.character.Bracket;
import com.pvpwow.lfg.domain.character.CharacterId;
import com.pvpwow.lfg.domain.character.CharacterName;
import com.pvpwow.lfg.domain.character.Realm;
import com.pvpwow.lfg.domain.character.Region;
import com.pvpwow.lfg.domain.character.Specialization;
import com.pvpwow.lfg.domain.character.WowClass;

import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Driving port: register a character owned by the current player.
 *
 * <p>Command shape follows the declared/observed seam (decided 2026-11-01):
 * declared facts arrive as value objects — pure data, valid by construction,
 * nothing left to add. Observed facts arrive RAW on purpose: the use case is
 * the witness of a self-report, so it owns the stamps. The policy "a
 * self-report is observed at registration" lives where registration happens,
 * and every future adapter (CLI, Blizzard sync) inherits it instead of
 * re-deciding it.
 */
public interface RegisterCharacter {

    CharacterId register(RegisterCharacterCommand command);

    record RegisterCharacterCommand(
            CharacterName name,
            Realm realm,
            Region region,
            WowClass wowClass,
            Set<Specialization> specs,
            int itemLevel,
            Map<Bracket, Integer> ratings) {

        public RegisterCharacterCommand {
            Objects.requireNonNull(name, "name must not be null");
            Objects.requireNonNull(realm, "realm must not be null");
            Objects.requireNonNull(region, "region must not be null");
            Objects.requireNonNull(wowClass, "wowClass must not be null");
            // Null collections are a client omission, not a wiring bug: they
            // normalize to empty and the DOMAIN decides the semantics (empty
            // specs is a business rejection; empty ratings simply means every
            // bracket stays unranked).
            specs = specs == null ? Set.of() : Set.copyOf(specs);
            ratings = ratings == null ? Map.of() : Map.copyOf(ratings);
        }
    }
}
