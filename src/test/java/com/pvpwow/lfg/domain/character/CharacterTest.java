package com.pvpwow.lfg.domain.character;

import com.pvpwow.lfg.domain.player.PlayerId;
import com.pvpwow.lfg.domain.shared.DomainRuleViolationException;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CharacterTest {

    private static final Instant OBSERVED_AT = Instant.parse("2026-11-01T10:00:00Z");

    @Test
    void registerCreatesACharacterWithGeneratedIdentity() {
        Character frostMage = registerMage(Set.of(Specialization.MAGE_FROST));

        assertThat(frostMage.id()).isNotNull();
        assertThat(frostMage.name().value()).isEqualTo("Frostbolt");
        assertThat(frostMage.realm().value()).isEqualTo("sanguino");
        assertThat(frostMage.region()).isEqualTo(Region.EU);
        assertThat(frostMage.wowClass()).isEqualTo(WowClass.MAGE);
        assertThat(frostMage.itemLevel().value()).isEqualTo(675);
        assertThat(frostMage.specs()).containsExactly(Specialization.MAGE_FROST);
    }

    @Test
    void twoRegistersAreDifferentCharactersEvenWithIdenticalData() {
        assertThat(registerMage(Set.of(Specialization.MAGE_FROST)))
                .isNotEqualTo(registerMage(Set.of(Specialization.MAGE_FROST)));
    }

    @Test
    void sameIdMeansSameCharacterEvenWithDifferentData() {
        CharacterId id = new CharacterId(UUID.randomUUID());
        Character at1800 = Character.rehydrate(id, ownerId(), new CharacterName("Frostbolt"),
                new Realm("Sanguino"), Region.EU, WowClass.MAGE, gear(675),
                Set.of(Specialization.MAGE_FROST), standings(1800));
        Character geared = Character.rehydrate(id, ownerId(), new CharacterName("Frostbolt"),
                new Realm("Sanguino"), Region.EU, WowClass.MAGE, gear(700),
                Set.of(Specialization.MAGE_FROST), standings(2400));

        assertThat(at1800).isEqualTo(geared);
        assertThat(at1800.hashCode()).isEqualTo(geared.hashCode());
    }

    @Test
    void rejectsNoSpecsAtAll() {
        assertThatThrownBy(() -> registerMage(Set.of()))
                .isInstanceOf(DomainRuleViolationException.class);
    }

    @Test
    void rejectsSpecsFromAnotherClassAndNamesTheCulprit() {
        assertThatThrownBy(() -> registerMage(Set.of(
                        Specialization.MAGE_FROST, Specialization.ROGUE_ASSASSINATION)))
                .isInstanceOf(DomainRuleViolationException.class)
                .hasMessageContaining("ROGUE_ASSASSINATION")
                .hasMessageContaining("MAGE");
    }

    @Test
    void aBracketWithoutStandingIsUnrankedNotAnError() {
        Character frostMage = registerMage(Set.of(Specialization.MAGE_FROST));

        assertThat(frostMage.ratingFor(Bracket.TWO_VS_TWO)).contains(new Rating(1800));
        assertThat(frostMage.ratingFor(Bracket.THREE_VS_THREE)).isEmpty();
        assertThat(frostMage.standingFor(Bracket.THREE_VS_THREE)).isEmpty();
    }

    @Test
    void theStandingCarriesTheSeasonAndTheObservationDate() {
        Character frostMage = registerMage(Set.of(Specialization.MAGE_FROST));

        PvpStanding standing = frostMage.standingFor(Bracket.TWO_VS_TWO).orElseThrow();

        assertThat(standing.rating()).isEqualTo(new Rating(1800));
        assertThat(standing.season()).isEqualTo(new PvpSeason(42));
        assertThat(standing.observedAt()).isEqualTo(OBSERVED_AT);
    }

    @Test
    void gearCarriesItsOwnObservationDate() {
        Character frostMage = registerMage(Set.of(Specialization.MAGE_FROST));

        assertThat(frostMage.gear().itemLevel()).isEqualTo(new ItemLevel(675));
        assertThat(frostMage.gear().observedAt()).isEqualTo(OBSERVED_AT);
    }

    @Test
    void rolesAreDerivedFromTheDeclaredSpecs() {
        Character frostMage = registerMage(Set.of(Specialization.MAGE_FROST, Specialization.MAGE_ARCANE));
        Character restoFeral = Character.register(ownerId(), new CharacterName("Hotw"),
                new Realm("Sanguino"), Region.EU, WowClass.DRUID, gear(670),
                Set.of(Specialization.DRUID_RESTORATION, Specialization.DRUID_FERAL), Map.of());

        assertThat(frostMage.roles()).containsExactly(Role.RANGED_DPS);
        assertThat(restoFeral.roles()).containsExactlyInAnyOrder(Role.HEALER, Role.MELEE_DPS);
    }

    @Test
    void playsAnswersWhetherAListingCouldPinThisSpec() {
        Character frostMage = registerMage(Set.of(Specialization.MAGE_FROST));

        assertThat(frostMage.plays(Specialization.MAGE_FROST)).isTrue();
        assertThat(frostMage.plays(Specialization.MAGE_ARCANE)).isFalse();
    }

    private Character registerMage(Set<Specialization> specs) {
        return Character.register(ownerId(), new CharacterName("Frostbolt"),
                new Realm("Sanguino"), Region.EU, WowClass.MAGE, gear(675),
                specs, standings(1800));
    }

    private GearSnapshot gear(int itemLevel) {
        return new GearSnapshot(new ItemLevel(itemLevel), OBSERVED_AT);
    }

    private Map<Bracket, PvpStanding> standings(int rating) {
        return Map.of(Bracket.TWO_VS_TWO,
                new PvpStanding(new Rating(rating), new PvpSeason(42), OBSERVED_AT));
    }

    private PlayerId ownerId() {
        return new PlayerId(UUID.randomUUID());
    }
}
