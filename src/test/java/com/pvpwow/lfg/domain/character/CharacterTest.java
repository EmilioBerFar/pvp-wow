package com.pvpwow.lfg.domain.character;

import com.pvpwow.lfg.domain.character.BlizzardCharacterId;
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
        Character at1800 = Character.rehydrate(id, ownerId(), new BlizzardCharacterId(161996209L), new CharacterName("Frostbolt"),
                new Realm("Sanguino"), Region.EU, WowClass.MAGE, gear(675),
                Set.of(Specialization.MAGE_FROST), standings(1800));
        Character geared = Character.rehydrate(id, ownerId(), new BlizzardCharacterId(161996209L), new CharacterName("Frostbolt"),
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
    void aCharacterWithoutABlizzardIdCannotExist() {
        // Every character comes from the import: a character registered by
        // hand, with a rating nobody verified, is not a character this
        // product has. So the type does not model one.
        assertThatThrownBy(() -> Character.register(ownerId(), null, new CharacterName("Frostbolt"),
                new Realm("Sanguino"), Region.EU, WowClass.MAGE, gear(675),
                Set.of(Specialization.MAGE_FROST), standings(1800)))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void theBlizzardIdIsTheCharactersIdentityInTheGame() {
        Character frostMage = registerMage(Set.of(Specialization.MAGE_FROST));

        assertThat(frostMage.blizzardId().value()).isEqualTo(161996209L);
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
        Character restoFeral = Character.register(ownerId(), new BlizzardCharacterId(161996209L), new CharacterName("Hotw"),
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

    // ---- the third door: observe() ----

    @Test
    void observeReplacesTheGearWithTheNewObservation() {
        Character frostMage = registerMage(Set.of(Specialization.MAGE_FROST));
        Instant later = Instant.parse("2026-11-19T09:00:00Z");

        Character reobserved = frostMage.observe(
                new GearSnapshot(new ItemLevel(702), later), standings(1800));

        assertThat(reobserved.itemLevel().value()).isEqualTo(702);
        assertThat(reobserved.gear().observedAt()).isEqualTo(later);
    }

    @Test
    void observeReplacesTheStandingsInsteadOfMergingThem() {
        // The seam, made explicit: an observed fact is superseded, never
        // added to. A rating of 1500 today does not live NEXT TO 1800 from
        // March - it replaces it. Merging would leave two truths about the
        // same bracket and only one of them would be current.
        Character at1800 = registerMage(Set.of(Specialization.MAGE_FROST));
        Instant later = Instant.parse("2026-11-19T09:00:00Z");

        Character at1500 = at1800.observe(gear(675), Map.of(Bracket.TWO_VS_TWO,
                new PvpStanding(new Rating(1500), new PvpSeason(43), later)));

        assertThat(at1500.ratingFor(Bracket.TWO_VS_TWO)).contains(new Rating(1500));
        assertThat(at1500.standingFor(Bracket.TWO_VS_TWO).orElseThrow().season())
                .isEqualTo(new PvpSeason(43));
        assertThat(at1500.standingFor(Bracket.TWO_VS_TWO).orElseThrow().observedAt())
                .isEqualTo(later);
    }

    @Test
    void observeForgetsTheBracketsTheNewObservationDoesNotCover() {
        // The consequence of replacing, stated as a fact: a bracket missing
        // from the new observation is UNRANKED now, not "we did not look".
        Character withBoth = Character.rehydrate(new CharacterId(UUID.randomUUID()), ownerId(),
                new BlizzardCharacterId(161996209L), new CharacterName("Frostbolt"), new Realm("Sanguino"), Region.EU, WowClass.MAGE,
                gear(675), Set.of(Specialization.MAGE_FROST),
                Map.of(Bracket.TWO_VS_TWO,
                        new PvpStanding(new Rating(1800), new PvpSeason(42), OBSERVED_AT),
                        Bracket.THREE_VS_THREE,
                        new PvpStanding(new Rating(2100), new PvpSeason(42), OBSERVED_AT)));

        Character onlyTwo = withBoth.observe(gear(675), Map.of(Bracket.TWO_VS_TWO,
                new PvpStanding(new Rating(1800), new PvpSeason(42), OBSERVED_AT)));

        assertThat(onlyTwo.standingFor(Bracket.THREE_VS_THREE)).isEmpty();
    }

    @Test
    void observeLeavesTheDeclaredSpecsAlone() {
        // The whole point of the declared/observed seam: Blizzard reports the
        // ACTIVE specialization, which is a fact about the character, not
        // about the player's intent to play it. An observation never rewrites
        // what the player declared they are willing to play.
        Character twoSpecs = registerMage(Set.of(Specialization.MAGE_FROST, Specialization.MAGE_ARCANE));

        Character reobserved = twoSpecs.observe(gear(700), standings(1800));

        assertThat(reobserved.specs()).containsExactlyInAnyOrder(
                Specialization.MAGE_FROST, Specialization.MAGE_ARCANE);
    }

    @Test
    void observeKeepsTheIdentityAndTheOwner() {
        Character frostMage = registerMage(Set.of(Specialization.MAGE_FROST));

        Character reobserved = frostMage.observe(gear(700), standings(1800));

        assertThat(reobserved.id()).isEqualTo(frostMage.id());
        assertThat(reobserved.ownerId()).isEqualTo(frostMage.ownerId());
        assertThat(reobserved.blizzardId()).isEqualTo(frostMage.blizzardId());
        assertThat(reobserved.name()).isEqualTo(frostMage.name());
        assertThat(reobserved.realm()).isEqualTo(frostMage.realm());
        assertThat(reobserved.region()).isEqualTo(frostMage.region());
        assertThat(reobserved.wowClass()).isEqualTo(frostMage.wowClass());
    }

    @Test
    void observeDoesNotMutateTheCharacterItWasCalledOn() {
        Character frostMage = registerMage(Set.of(Specialization.MAGE_FROST));

        Character reobserved = frostMage.observe(gear(700), standings(1800));

        assertThat(frostMage.itemLevel().value()).isEqualTo(675);
        assertThat(reobserved.itemLevel().value()).isEqualTo(700);
    }

    @Test
    void observeAcceptsAnEmptyObservationOfStandings() {
        // A character Blizzard has no arena record for is a normal state:
        // unranked, not broken. The domain must not turn "no rating" into an
        // error.
        Character frostMage = registerMage(Set.of(Specialization.MAGE_FROST));

        Character unranked = frostMage.observe(gear(700), Map.of());

        assertThat(unranked.standings()).isEmpty();
        assertThat(unranked.ratingFor(Bracket.TWO_VS_TWO)).isEmpty();
    }

    private Character registerMage(Set<Specialization> specs) {
        return Character.register(ownerId(), new BlizzardCharacterId(161996209L), new CharacterName("Frostbolt"),
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
