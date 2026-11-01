package com.pvpwow.lfg.adapters.out.persistence;

import com.pvpwow.lfg.domain.character.Bracket;
import com.pvpwow.lfg.domain.character.Character;
import com.pvpwow.lfg.domain.character.CharacterName;
import com.pvpwow.lfg.domain.character.GearSnapshot;
import com.pvpwow.lfg.domain.character.ItemLevel;
import com.pvpwow.lfg.domain.character.PvpSeason;
import com.pvpwow.lfg.domain.character.PvpStanding;
import com.pvpwow.lfg.domain.character.Rating;
import com.pvpwow.lfg.domain.character.Realm;
import com.pvpwow.lfg.domain.character.Region;
import com.pvpwow.lfg.domain.character.Specialization;
import com.pvpwow.lfg.domain.character.WowClass;
import com.pvpwow.lfg.domain.player.PlayerId;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The translation must be TOTAL: any field the mapper forgets is a field the
 * database silently loses. No database here — this test fails first, and it
 * costs milliseconds.
 */
class CharacterEntityMapperTest {

    private static final Instant OBSERVED_AT = Instant.parse("2026-11-01T10:00:00Z");

    @Test
    void roundTripsEveryFieldOfTheAggregate() {
        Character original = Character.register(
                new PlayerId(UUID.randomUUID()),
                new CharacterName("Éowyn"),
                new Realm("Los  Errantes"),
                Region.EU,
                WowClass.MAGE,
                new GearSnapshot(new ItemLevel(675), OBSERVED_AT),
                Set.of(Specialization.MAGE_FROST, Specialization.MAGE_ARCANE),
                Map.of(Bracket.TWO_VS_TWO,
                        new PvpStanding(new Rating(1800), new PvpSeason(42), OBSERVED_AT)));

        Character rehydrated = CharacterEntityMapper.toDomain(
                CharacterEntityMapper.toEntity(original));

        assertThat(rehydrated.id()).isEqualTo(original.id());
        assertThat(rehydrated.ownerId()).isEqualTo(original.ownerId());
        assertThat(rehydrated.name()).isEqualTo(original.name());
        assertThat(rehydrated.realm()).isEqualTo(original.realm());
        assertThat(rehydrated.region()).isEqualTo(original.region());
        assertThat(rehydrated.wowClass()).isEqualTo(original.wowClass());
        assertThat(rehydrated.gear()).isEqualTo(original.gear());
        assertThat(rehydrated.specs()).isEqualTo(original.specs());
        assertThat(rehydrated.standings()).isEqualTo(original.standings());
        assertThat(rehydrated.standings().get(Bracket.TWO_VS_TWO).observedAt())
                .isEqualTo(OBSERVED_AT);
    }

    @Test
    void anUnrankedCharacterRoundTripsWithAnEmptyStandingsMap() {
        Character unranked = Character.register(
                new PlayerId(UUID.randomUUID()),
                new CharacterName("Tofuru"),
                new Realm("Illidan"),
                Region.US,
                WowClass.ROGUE,
                new GearSnapshot(new ItemLevel(600), OBSERVED_AT),
                Set.of(Specialization.ROGUE_SUBTLETY),
                Map.of());

        Character rehydrated = CharacterEntityMapper.toDomain(
                CharacterEntityMapper.toEntity(unranked));

        assertThat(rehydrated.standings()).isEmpty();
        assertThat(rehydrated.specs()).containsExactly(Specialization.ROGUE_SUBTLETY);
    }
}
