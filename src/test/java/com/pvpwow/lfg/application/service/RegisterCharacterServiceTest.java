package com.pvpwow.lfg.application.service;

import com.pvpwow.lfg.application.port.in.RegisterCharacter.RegisterCharacterCommand;
import com.pvpwow.lfg.application.port.out.Characters;
import com.pvpwow.lfg.domain.character.Bracket;
import com.pvpwow.lfg.domain.character.Character;
import com.pvpwow.lfg.domain.character.CharacterId;
import com.pvpwow.lfg.domain.character.CharacterName;
import com.pvpwow.lfg.domain.character.ItemLevel;
import com.pvpwow.lfg.domain.character.PvpSeason;
import com.pvpwow.lfg.domain.character.PvpStanding;
import com.pvpwow.lfg.domain.character.Rating;
import com.pvpwow.lfg.domain.character.Realm;
import com.pvpwow.lfg.domain.character.Region;
import com.pvpwow.lfg.domain.character.Specialization;
import com.pvpwow.lfg.domain.character.WowClass;
import com.pvpwow.lfg.domain.player.PlayerId;
import com.pvpwow.lfg.domain.shared.DomainRuleViolationException;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RegisterCharacterServiceTest {

    private static final Instant NOW = Instant.parse("2026-11-01T10:00:00Z");
    private static final PlayerId OWNER = new PlayerId(UUID.randomUUID());

    private final FakeCharacters characters = new FakeCharacters();
    private final RegisterCharacterService service = new RegisterCharacterService(
            characters,
            () -> OWNER,
            () -> new PvpSeason(42),
            Clock.fixed(NOW, ZoneOffset.UTC));

    @Test
    void registersACharacterAndStampsTheSelfReportAsObservedNow() {
        CharacterId id = service.register(command("Frostbolt", "sanguino", 675,
                Map.of(Bracket.TWO_VS_TWO, 1800)));

        Character saved = characters.onlySaved();
        assertThat(saved.id()).isEqualTo(id);
        assertThat(saved.ownerId()).isEqualTo(OWNER);
        assertThat(saved.gear().itemLevel()).isEqualTo(new ItemLevel(675));
        assertThat(saved.gear().observedAt()).isEqualTo(NOW);
        PvpStanding standing = saved.standingFor(Bracket.TWO_VS_TWO).orElseThrow();
        assertThat(standing.rating()).isEqualTo(new Rating(1800));
        assertThat(standing.season()).isEqualTo(new PvpSeason(42));
        assertThat(standing.observedAt()).isEqualTo(NOW);
    }

    @Test
    void ratingsAreOptionalAndAMissingBracketStaysUnranked() {
        service.register(command("Frostbolt", "sanguino", 675, Map.of()));

        assertThat(characters.onlySaved().standings()).isEmpty();
    }

    @Test
    void rejectsANameAlreadyRegisteredOnThatRealmAndRegion() {
        service.register(command("Frostbolt", "sanguino", 675, Map.of()));

        assertThatThrownBy(() -> service.register(command("Frostbolt", "sanguino", 700, Map.of())))
                .isInstanceOf(DomainRuleViolationException.class)
                .hasMessageContaining("already registered");
        assertThat(characters.savedCount()).isEqualTo(1);
    }

    @Test
    void theSameNameOnAnotherRealmIsNotADuplicate() {
        service.register(command("Frostbolt", "sanguino", 675, Map.of()));

        assertThat(service.register(command("Frostbolt", "dun-modr", 675, Map.of()))).isNotNull();
        assertThat(characters.savedCount()).isEqualTo(2);
    }

    @Test
    void theSameNameInAnotherRegionIsNotADuplicate() {
        service.register(command("Frostbolt", "sanguino", 675, Map.of()));

        CharacterId sameNameOnAnAmericanRealm = service.register(new RegisterCharacterCommand(
                new CharacterName("Frostbolt"), new Realm("sanguino"), Region.US,
                WowClass.MAGE, Set.of(Specialization.MAGE_FROST), 675, Map.of()));

        assertThat(sameNameOnAnAmericanRealm).isNotNull();
        assertThat(characters.savedCount()).isEqualTo(2);
    }

    @Test
    void observedDataIsValidatedAtTheMomentItIsStamped() {
        assertThatThrownBy(() -> service.register(command("Frostbolt", "sanguino", 0, Map.of())))
                .isInstanceOf(DomainRuleViolationException.class);
        assertThatThrownBy(() -> service.register(command("Frostbolt", "sanguino", 675,
                Map.of(Bracket.THREE_VS_THREE, -100))))
                .isInstanceOf(DomainRuleViolationException.class);
        assertThat(characters.savedCount()).isZero();
    }

    @Test
    void domainRulesFireThroughTheUseCase() {
        RegisterCharacterCommand rogueSpecOnAMage = new RegisterCharacterCommand(
                new CharacterName("Frostbolt"), new Realm("sanguino"), Region.EU, WowClass.MAGE,
                Set.of(Specialization.MAGE_FROST, Specialization.ROGUE_ASSASSINATION),
                675, Map.of());

        assertThatThrownBy(() -> service.register(rogueSpecOnAMage))
                .isInstanceOf(DomainRuleViolationException.class)
                .hasMessageContaining("ROGUE_ASSASSINATION");
    }

    private RegisterCharacterCommand command(String name, String realm, int itemLevel,
                                             Map<Bracket, Integer> ratings) {
        return new RegisterCharacterCommand(new CharacterName(name), new Realm(realm), Region.EU,
                WowClass.MAGE, Set.of(Specialization.MAGE_FROST), itemLevel, ratings);
    }

    /**
     * Hand-rolled fake — that is what ports are for. Domain tests stay
     * mockless; application tests fake the edges.
     */
    private static final class FakeCharacters implements Characters {

        private final Set<String> registered = new HashSet<>();
        private final List<Character> saved = new ArrayList<>();

        @Override
        public void save(Character character) {
            saved.add(character);
            registered.add(key(character.region(), character.realm(), character.name()));
        }

        @Override
        public boolean alreadyRegistered(Region region, Realm realm, CharacterName name) {
            return registered.contains(key(region, realm, name));
        }

        Character onlySaved() {
            assertThat(saved).hasSize(1);
            return saved.get(0);
        }

        int savedCount() {
            return saved.size();
        }

        private String key(Region region, Realm realm, CharacterName name) {
            return "%s|%s|%s".formatted(region, realm.value(), name.value());
        }
    }
}
