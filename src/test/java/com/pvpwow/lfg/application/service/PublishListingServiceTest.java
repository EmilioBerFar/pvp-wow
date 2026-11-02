package com.pvpwow.lfg.application.service;

import com.pvpwow.lfg.application.error.ResourceNotFoundException;
import com.pvpwow.lfg.application.port.in.PublishListing.PublishListingCommand;
import com.pvpwow.lfg.application.port.out.Characters;
import com.pvpwow.lfg.application.port.out.Listings;
import com.pvpwow.lfg.domain.character.Bracket;
import com.pvpwow.lfg.domain.character.Character;
import com.pvpwow.lfg.domain.character.CharacterId;
import com.pvpwow.lfg.domain.character.CharacterName;
import com.pvpwow.lfg.domain.character.GearSnapshot;
import com.pvpwow.lfg.domain.character.ItemLevel;
import com.pvpwow.lfg.domain.character.Realm;
import com.pvpwow.lfg.domain.character.Region;
import com.pvpwow.lfg.domain.character.Specialization;
import com.pvpwow.lfg.domain.character.WowClass;
import com.pvpwow.lfg.domain.listing.AvailabilitySchedule;
import com.pvpwow.lfg.domain.listing.Language;
import com.pvpwow.lfg.domain.listing.LfgListing;
import com.pvpwow.lfg.domain.listing.ListingId;
import com.pvpwow.lfg.domain.listing.ListingStatus;
import com.pvpwow.lfg.domain.listing.TimeWindow;
import com.pvpwow.lfg.domain.player.PlayerId;
import com.pvpwow.lfg.domain.shared.DomainRuleViolationException;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.DayOfWeek;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PublishListingServiceTest {

    private static final Instant NOW = Instant.parse("2026-11-02T10:00:00Z");
    private static final Duration TTL = Duration.ofHours(24);
    private static final PlayerId OWNER = new PlayerId(UUID.randomUUID());
    private static final CharacterId CHARACTER_ID = new CharacterId(UUID.randomUUID());
    private static final AvailabilitySchedule SCHEDULE = new AvailabilitySchedule(
            Set.of(new TimeWindow(DayOfWeek.MONDAY, LocalTime.of(18, 0), LocalTime.of(22, 0))),
            ZoneId.of("Europe/Madrid"));

    private final FakeCharacters characters = new FakeCharacters();
    private final FakeListings listings = new FakeListings();
    private final PublishListingService service = serviceAt(NOW);

    @Test
    void publishesForTheOwnerAndAppliesTheServerTtl() {
        characters.store(characterOwnedBy(OWNER));

        ListingId id = service.publish(command(Bracket.TWO_VS_TWO, Specialization.MAGE_FROST));

        LfgListing saved = listings.onlySaved();
        assertThat(saved.id()).isEqualTo(id);
        assertThat(saved.characterId()).isEqualTo(CHARACTER_ID);
        assertThat(saved.status()).isEqualTo(ListingStatus.ACTIVE);
        assertThat(saved.createdAt()).isEqualTo(NOW);
        assertThat(saved.expiresAt()).isEqualTo(NOW.plus(TTL));
    }

    @Test
    void rejectsAnUnregisteredCharacter() {
        assertThatThrownBy(() -> service.publish(
                command(Bracket.TWO_VS_TWO, Specialization.MAGE_FROST)))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("Character not found");
        assertThat(listings.saved).isEmpty();
    }

    @Test
    void rejectsACharacterOwnedBySomeoneElse() {
        characters.store(characterOwnedBy(new PlayerId(UUID.randomUUID())));

        assertThatThrownBy(() -> service.publish(
                command(Bracket.TWO_VS_TWO, Specialization.MAGE_FROST)))
                .isInstanceOf(ResourceNotFoundException.class)
                .as("same failure as an unknown id: never reveal that the character exists")
                .hasMessage("Character not found");
        assertThat(listings.saved).isEmpty();
    }

    @Test
    void rejectsASpecTheCharacterDidNotDeclare() {
        characters.store(characterOwnedBy(OWNER));

        assertThatThrownBy(() -> service.publish(
                command(Bracket.TWO_VS_TWO, Specialization.MAGE_ARCANE)))
                .isInstanceOf(DomainRuleViolationException.class)
                .hasMessageContaining("does not play MAGE_ARCANE");
        assertThat(listings.saved).isEmpty();
    }

    @Test
    void rejectsASecondActiveListingForTheSameCharacterAndBracket() {
        characters.store(characterOwnedBy(OWNER));
        service.publish(command(Bracket.TWO_VS_TWO, Specialization.MAGE_FROST));

        assertThatThrownBy(() -> service.publish(
                command(Bracket.TWO_VS_TWO, Specialization.MAGE_FROST)))
                .isInstanceOf(DomainRuleViolationException.class)
                .hasMessageContaining("already has an active TWO_VS_TWO listing");
        assertThat(listings.saved).hasSize(1);
    }

    @Test
    void twoBracketsAreIndependent() {
        characters.store(characterOwnedBy(OWNER));
        service.publish(command(Bracket.TWO_VS_TWO, Specialization.MAGE_FROST));

        service.publish(command(Bracket.THREE_VS_THREE, Specialization.MAGE_FROST));

        assertThat(listings.saved).hasSize(2);
    }

    @Test
    void anExpiredListingDoesNotBlockAReplacementEvenBeforeCleanupRuns() {
        characters.store(characterOwnedBy(OWNER));
        service.publish(command(Bracket.TWO_VS_TWO, Specialization.MAGE_FROST));
        PublishListingService afterExpiry = serviceAt(NOW.plus(TTL));

        afterExpiry.publish(command(Bracket.TWO_VS_TWO, Specialization.MAGE_FROST));

        assertThat(listings.saved).hasSize(2);
        assertThat(listings.saved.get(0).status())
                .as("reading expiry does not secretly mutate the old aggregate")
                .isEqualTo(ListingStatus.ACTIVE);
    }

    private PublishListingService serviceAt(Instant now) {
        return new PublishListingService(characters, listings, () -> OWNER,
                Clock.fixed(now, ZoneOffset.UTC), TTL);
    }

    private PublishListingCommand command(Bracket bracket, Specialization spec) {
        return new PublishListingCommand(CHARACTER_ID, bracket, spec, Set.of(Language.ES), SCHEDULE);
    }

    private Character characterOwnedBy(PlayerId owner) {
        return Character.rehydrate(CHARACTER_ID, owner, new CharacterName("Frostbolt"),
                new Realm("sanguino"), Region.EU, WowClass.MAGE,
                new GearSnapshot(new ItemLevel(675), NOW),
                Set.of(Specialization.MAGE_FROST), Map.of());
    }

    private static final class FakeCharacters implements Characters {

        private Character stored;

        void store(Character character) {
            stored = character;
        }

        @Override
        public void save(Character character) {
            stored = character;
        }

        @Override
        public Optional<Character> findById(CharacterId id) {
            return Optional.ofNullable(stored).filter(character -> character.id().equals(id));
        }

        @Override
        public boolean alreadyRegistered(Region region, Realm realm, CharacterName name) {
            return false;
        }
    }

    private static final class FakeListings implements Listings {

        private final List<LfgListing> saved = new ArrayList<>();

        @Override
        public void publish(LfgListing listing) {
            saved.add(listing);
        }

        @Override
        public boolean hasActiveFor(CharacterId characterId, Bracket bracket, Instant now) {
            return saved.stream().anyMatch(listing -> listing.characterId().equals(characterId)
                    && listing.bracket() == bracket
                    && listing.isActive()
                    && listing.expiresAt().isAfter(now));
        }

        LfgListing onlySaved() {
            assertThat(saved).hasSize(1);
            return saved.getFirst();
        }
    }
}
