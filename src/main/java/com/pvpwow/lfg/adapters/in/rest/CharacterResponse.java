package com.pvpwow.lfg.adapters.in.rest;

import com.pvpwow.lfg.domain.character.Bracket;
import com.pvpwow.lfg.domain.character.Character;
import com.pvpwow.lfg.domain.character.Region;
import com.pvpwow.lfg.domain.character.Specialization;
import com.pvpwow.lfg.domain.character.WowClass;

import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * The owner's own character, as the wire sees it.
 *
 * <p>Unlike the board projection this view carries EVERY bracket's standing:
 * it is your own character sheet, not a public candidate card. It carries no
 * {@code ownerId} either — the caller already knows who they are, and the field
 * would only be noise. No stale flags here: this endpoint returns the observed
 * evidence with its dates, and the freshness policy belongs to the board read.
 */
record CharacterResponse(
        UUID id,
        String name,
        String realm,
        Region region,
        WowClass wowClass,
        List<Specialization> specs,
        int itemLevel,
        Instant gearObservedAt,
        Map<String, Standing> standings) {

    static CharacterResponse from(Character character) {
        Map<String, Standing> standings = new LinkedHashMap<>();
        for (Bracket bracket : Bracket.values()) {
            character.standingFor(bracket).ifPresent(standing ->
                    standings.put(WireParsing.bracketKey(bracket), new Standing(
                            standing.rating().value(), standing.season().id(),
                            standing.observedAt())));
        }
        return new CharacterResponse(
                character.id().value(),
                character.name().value(),
                character.realm().value(),
                character.region(),
                character.wowClass(),
                character.specs().stream().sorted().toList(),
                character.itemLevel().value(),
                character.gear().observedAt(),
                Collections.unmodifiableMap(standings));
    }

    record Standing(int rating, int season, Instant observedAt) {
    }
}
