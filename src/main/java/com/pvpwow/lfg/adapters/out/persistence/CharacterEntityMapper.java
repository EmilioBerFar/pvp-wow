package com.pvpwow.lfg.adapters.out.persistence;

import com.pvpwow.lfg.domain.character.Bracket;
import com.pvpwow.lfg.domain.character.Character;
import com.pvpwow.lfg.domain.character.CharacterId;
import com.pvpwow.lfg.domain.character.CharacterName;
import com.pvpwow.lfg.domain.character.GearSnapshot;
import com.pvpwow.lfg.domain.character.ItemLevel;
import com.pvpwow.lfg.domain.character.PvpSeason;
import com.pvpwow.lfg.domain.character.PvpStanding;
import com.pvpwow.lfg.domain.character.Rating;
import com.pvpwow.lfg.domain.character.Realm;
import com.pvpwow.lfg.domain.player.PlayerId;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * The only place where columns become meaning. Both directions are total:
 * every field of the aggregate has a column, and every column has a field.
 *
 * <p>Note the two doors being respected: {@code toDomain} always uses
 * {@link Character#rehydrate} because the identity already exists in the
 * database — repositories never invent ids, and a mapper is a repository's
 * mouth.
 */
final class CharacterEntityMapper {

    private CharacterEntityMapper() {
    }

    static CharacterEntity toEntity(Character character) {
        CharacterEntity entity = new CharacterEntity();
        entity.setId(character.id().value());
        entity.setOwnerId(character.ownerId().value());
        entity.setName(character.name().value());
        entity.setRealm(character.realm().value());
        entity.setRegion(character.region());
        entity.setWowClass(character.wowClass());
        entity.setItemLevel(character.gear().itemLevel().value());
        entity.setGearObservedAt(character.gear().observedAt());

        Set<com.pvpwow.lfg.domain.character.Specialization> specs = new HashSet<>();
        character.specs().forEach(spec -> specs.add(spec));
        entity.setSpecs(specs);

        Map<Bracket, StandingEmbeddable> standings = new HashMap<>();
        character.standings().forEach((bracket, standing) -> standings.put(bracket,
                StandingEmbeddable.of(standing.rating().value(),
                        standing.season().id(),
                        standing.observedAt())));
        entity.setStandings(standings);
        return entity;
    }

    static Character toDomain(CharacterEntity entity) {
        Map<Bracket, PvpStanding> standings = new HashMap<>();
        entity.standings().forEach((bracket, standing) -> standings.put(bracket,
                new PvpStanding(new Rating(standing.rating()),
                        new PvpSeason(standing.seasonId()),
                        standing.observedAt())));

        return Character.rehydrate(
                new CharacterId(entity.id()),
                new PlayerId(entity.ownerId()),
                new CharacterName(entity.name()),
                new Realm(entity.realm()),
                entity.region(),
                entity.wowClass(),
                new GearSnapshot(new ItemLevel(entity.itemLevel()), entity.gearObservedAt()),
                Set.copyOf(entity.specs()),
                standings);
    }
}
