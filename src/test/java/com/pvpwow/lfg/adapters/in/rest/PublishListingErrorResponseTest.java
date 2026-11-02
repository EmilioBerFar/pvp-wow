package com.pvpwow.lfg.adapters.in.rest;

import com.pvpwow.lfg.application.error.ResourceNotFoundException;
import com.pvpwow.lfg.application.port.in.PublishListing.PublishListingCommand;
import com.pvpwow.lfg.application.port.out.Characters;
import com.pvpwow.lfg.application.port.out.Listings;
import com.pvpwow.lfg.application.service.PublishListingService;
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
import com.pvpwow.lfg.domain.listing.TimeWindow;
import com.pvpwow.lfg.domain.player.PlayerId;
import org.junit.jupiter.api.Test;
import org.springframework.http.ProblemDetail;

import java.time.Clock;
import java.time.DayOfWeek;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Anti-enumeration contract: an unknown character id and a character owned by
 * another player must produce the exact same response. The test runs the REAL
 * service and the REAL advice, so a divergence in either layer fails here.
 */
class PublishListingErrorResponseTest {

    private static final Instant NOW = Instant.parse("2026-11-02T10:00:00Z");
    private static final Duration TTL = Duration.ofHours(24);
    private static final PlayerId CALLER = new PlayerId(UUID.randomUUID());
    private static final CharacterId CHARACTER_ID = new CharacterId(UUID.randomUUID());
    private static final AvailabilitySchedule SCHEDULE = new AvailabilitySchedule(
            Set.of(new TimeWindow(DayOfWeek.MONDAY, LocalTime.of(18, 0), LocalTime.of(22, 0))),
            ZoneId.of("Europe/Madrid"));

    private final FakeCharacters characters = new FakeCharacters();
    private final PublishListingService service = new PublishListingService(
            characters, new FakeListings(), () -> CALLER,
            Clock.fixed(NOW, ZoneOffset.UTC), TTL);
    private final ApiErrorHandling advice = new ApiErrorHandling();

    @Test
    void anUnknownCharacterAndAnotherPlayersCharacterReturnTheSameResponse() {
        ProblemDetail unknown = responseFor(command());

        characters.store(characterOwnedBy(new PlayerId(UUID.randomUUID())));
        ProblemDetail foreign = responseFor(command());

        assertThat(foreign.getType()).isEqualTo(unknown.getType());
        assertThat(foreign.getTitle()).isEqualTo(unknown.getTitle());
        assertThat(foreign.getStatus()).isEqualTo(unknown.getStatus());
        assertThat(foreign.getDetail()).isEqualTo(unknown.getDetail());
        assertThat(foreign.getInstance()).isEqualTo(unknown.getInstance());
        assertThat(foreign.getProperties()).isEqualTo(unknown.getProperties());

        assertThat(unknown.getStatus()).isEqualTo(404);
        assertThat(unknown.getDetail()).isEqualTo("Character not found");
        assertThat(unknown.getProperties())
                .as("no name, realm or region may leak")
                .isNullOrEmpty();
    }

    private ProblemDetail responseFor(PublishListingCommand command) {
        try {
            service.publish(command);
            throw new AssertionError("expected ResourceNotFoundException");
        } catch (ResourceNotFoundException notFound) {
            return advice.resourceNotFound(notFound);
        }
    }

    private PublishListingCommand command() {
        return new PublishListingCommand(CHARACTER_ID, Bracket.TWO_VS_TWO,
                Specialization.MAGE_FROST, Set.of(Language.ES), SCHEDULE);
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

        @Override
        public void publish(LfgListing listing) {
            throw new AssertionError("publishing must not be reached in these cases");
        }

        @Override
        public boolean hasActiveFor(CharacterId characterId, Bracket bracket, Instant now) {
            throw new AssertionError("duplicate check must not be reached in these cases");
        }
    }
}
