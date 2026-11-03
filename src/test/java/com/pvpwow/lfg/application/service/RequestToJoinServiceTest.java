package com.pvpwow.lfg.application.service;

import com.pvpwow.lfg.application.error.ResourceNotFoundException;
import com.pvpwow.lfg.application.port.in.RequestToJoin.RequestToJoinCommand;
import com.pvpwow.lfg.application.port.out.Characters;
import com.pvpwow.lfg.application.port.out.CurrentPlayerProvider;
import com.pvpwow.lfg.application.port.out.ListingLifecycle;
import com.pvpwow.lfg.domain.character.Character;
import com.pvpwow.lfg.domain.character.CharacterId;
import com.pvpwow.lfg.domain.character.CharacterName;
import com.pvpwow.lfg.domain.character.GearSnapshot;
import com.pvpwow.lfg.domain.character.ItemLevel;
import com.pvpwow.lfg.domain.character.Realm;
import com.pvpwow.lfg.domain.character.Region;
import com.pvpwow.lfg.domain.character.Specialization;
import com.pvpwow.lfg.domain.character.WowClass;
import com.pvpwow.lfg.domain.join.JoinRequest;
import com.pvpwow.lfg.domain.join.JoinRequestId;
import com.pvpwow.lfg.domain.listing.LfgListing;
import com.pvpwow.lfg.domain.listing.ListingId;
import com.pvpwow.lfg.domain.player.PlayerId;
import com.pvpwow.lfg.domain.shared.DomainRuleViolationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
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

class RequestToJoinServiceTest {

    private static final Instant NOW = Instant.parse("2026-11-03T10:00:00Z");
    private static final PlayerId REQUESTER_OWNER = new PlayerId(UUID.randomUUID());
    private static final PlayerId LISTING_OWNER = new PlayerId(UUID.randomUUID());
    private static final CharacterId REQUESTER = new CharacterId(UUID.randomUUID());
    private static final CharacterId OWNER = new CharacterId(UUID.randomUUID());
    private static final Duration TTL = Duration.ofHours(24);

    private final FakeCharacters characters = new FakeCharacters();
    private final FakeLifecycle lifecycle = new FakeLifecycle();
    private final RequestToJoinService service = new RequestToJoinService(characters, lifecycle,
            () -> REQUESTER_OWNER, Clock.fixed(NOW, ZoneOffset.UTC));

    @BeforeEach
    void reset() {
        characters.reset();
        lifecycle.reset();
    }

    @Test
    void askingCreatesAPendingRequestThatLivesAsLongAsTheListing() {
        characters.store(character(REQUESTER, REQUESTER_OWNER));
        LfgListing listing = publish(OWNER);
        lifecycle.storeListing(listing);

        JoinRequestId id = service.request(new RequestToJoinCommand(REQUESTER, listing.id()));

        JoinRequest request = lifecycle.onlyRequest();
        assertThat(request.id()).isEqualTo(id);
        assertThat(request.status()).isEqualTo(com.pvpwow.lfg.domain.join.JoinRequestStatus.PENDING);
        assertThat(request.createdAt()).isEqualTo(NOW);
        assertThat(request.expiresAt())
                .as("the request lives exactly as long as the listing")
                .isEqualTo(listing.expiresAt());
    }

    @Test
    void askingForYourOwnListingIsRejected() {
        characters.store(character(OWNER, REQUESTER_OWNER));
        LfgListing listing = publish(OWNER);
        lifecycle.storeListing(listing);

        assertThatThrownBy(() -> service.request(new RequestToJoinCommand(OWNER, listing.id())))
                .isInstanceOf(DomainRuleViolationException.class)
                .hasMessageContaining("cannot request to join your own listing");
    }

    @Test
    void askingForAListingThatDoesNotExistIs404() {
        characters.store(character(REQUESTER, REQUESTER_OWNER));

        assertThatThrownBy(() -> service.request(
                new RequestToJoinCommand(REQUESTER, new ListingId(UUID.randomUUID()))))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("Listing not found");
    }

    @Test
    void askingWithSomebodyElsesCharacterIsTheSame404AsAnAbsentCharacter() {
        characters.store(character(OWNER, LISTING_OWNER));
        LfgListing listing = publish(OWNER);
        lifecycle.storeListing(listing);

        assertThatThrownBy(() -> service.request(new RequestToJoinCommand(OWNER, listing.id())))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("Character not found");
    }

    @Test
    void askingForAListingThatIsNoLongerOpenIsRejected() {
        characters.store(character(REQUESTER, REQUESTER_OWNER));
        LfgListing listing = publish(OWNER);
        lifecycle.storeListing(listing);
        lifecycle.expireListing(listing.id());

        assertThatThrownBy(() -> service.request(new RequestToJoinCommand(REQUESTER, listing.id())))
                .isInstanceOf(DomainRuleViolationException.class)
                .hasMessageContaining("no longer open for requests");
    }

    @Test
    void askingTwiceForTheSameListingIsRejected() {
        characters.store(character(REQUESTER, REQUESTER_OWNER));
        LfgListing listing = publish(OWNER);
        lifecycle.storeListing(listing);

        service.request(new RequestToJoinCommand(REQUESTER, listing.id()));

        assertThatThrownBy(() -> service.request(new RequestToJoinCommand(REQUESTER, listing.id())))
                .isInstanceOf(DomainRuleViolationException.class)
                .hasMessageContaining("already have a pending request");
    }

    @Test
    void aPlayerCannotHoldMoreThanThreePendingRequests() {
        characters.store(character(REQUESTER, REQUESTER_OWNER));
        for (int i = 0; i < 3; i++) {
            LfgListing listing = publish(OWNER);
            lifecycle.storeListing(listing);
            service.request(new RequestToJoinCommand(REQUESTER, listing.id()));
        }
        LfgListing fourth = publish(OWNER);
        lifecycle.storeListing(fourth);

        assertThatThrownBy(() -> service.request(new RequestToJoinCommand(REQUESTER, fourth.id())))
                .isInstanceOf(DomainRuleViolationException.class)
                .hasMessageContaining("pending requests (the limit is 3)");
    }

    @Test
    void requestsThatOutlivedTheirListingDoNotCountTowardsTheLimit() {
        characters.store(character(REQUESTER, REQUESTER_OWNER));
        for (int i = 0; i < 3; i++) {
            LfgListing listing = publish(OWNER);
            lifecycle.storeListing(listing);
            service.request(new RequestToJoinCommand(REQUESTER, listing.id()));
            // The listing dies, so the request waiting for an answer to it is
            // expired by definition: it must not keep the slot.
            lifecycle.expireRequest(listing.id());
        }
        LfgListing fourth = publish(OWNER);
        lifecycle.storeListing(fourth);

        service.request(new RequestToJoinCommand(REQUESTER, fourth.id()));

        assertThat(lifecycle.requests())
                .as("three dead requests are not three live requests")
                .hasSize(4);
    }

    private LfgListing publish(CharacterId characterId) {
        return LfgListing.publish(characterId, com.pvpwow.lfg.domain.character.Bracket.THREE_VS_THREE,
                Specialization.MAGE_FROST, Set.of(com.pvpwow.lfg.domain.listing.Language.ES),
                new com.pvpwow.lfg.domain.listing.AvailabilitySchedule(
                        Set.of(new com.pvpwow.lfg.domain.listing.TimeWindow(
                                java.time.DayOfWeek.MONDAY, LocalTime.of(18, 0),
                                LocalTime.of(22, 0))), ZoneId.of("Europe/Madrid")),
                NOW, TTL);
    }

    private static Character character(CharacterId id, PlayerId owner) {
        return Character.rehydrate(id, owner, new CharacterName("Frostbolt"),
                new Realm("sanguino"), Region.EU, WowClass.MAGE,
                new GearSnapshot(new ItemLevel(675), NOW),
                Set.of(Specialization.MAGE_FROST), Map.of());
    }

    private static final class FakeCharacters implements Characters {

        private final Map<CharacterId, Character> stored = new java.util.HashMap<>();

        void store(Character character) {
            stored.put(character.id(), character);
        }

        void reset() {
            stored.clear();
        }

        @Override
        public void save(Character character) {
            stored.put(character.id(), character);
        }

        @Override
        public Optional<Character> findById(CharacterId id) {
            return Optional.ofNullable(stored.get(id));
        }

        @Override
        public boolean alreadyRegistered(Region region, Realm realm, CharacterName name) {
            return false;
        }
    }

    private static final class FakeLifecycle implements ListingLifecycle {

        private final Map<ListingId, LfgListing> listings = new java.util.HashMap<>();
        private final List<JoinRequest> requests = new ArrayList<>();

        void storeListing(LfgListing listing) {
            listings.put(listing.id(), listing);
        }

        void expireListing(ListingId listingId) {
            LfgListing listing = listings.get(listingId);
            listing.expireIfDue(NOW.plus(Duration.ofHours(25)));
            listings.put(listingId, listing);
        }

        /**
         * The listing dies, so the request waiting for an answer to it is
         * expired by definition.
         */
        void expireRequest(ListingId listingId) {
            requests.stream()
                    .filter(request -> request.listingId().equals(listingId))
                    .forEach(request -> request.expireIfDue(NOW.plus(Duration.ofHours(25))));
        }

        void reset() {
            listings.clear();
            requests.clear();
        }

        List<JoinRequest> requests() {
            return List.copyOf(requests);
        }

        JoinRequest onlyRequest() {
            assertThat(requests).hasSize(1);
            return requests.getFirst();
        }

        @Override
        public List<com.pvpwow.lfg.domain.listing.ListingOutcome> completeGroup(
                JoinRequest acceptedRequest, CharacterId ownerId, CharacterId requesterId,
                PlayerId ownerPlayerId, PlayerId requesterOwnerId, Instant now) {
            throw new AssertionError("asking must not close anything");
        }

        @Override
        public com.pvpwow.lfg.domain.listing.ListingOutcome withdraw(ListingId listingId,
                CharacterId characterId, Instant now) {
            throw new AssertionError("asking must not close anything");
        }

        @Override
        public com.pvpwow.lfg.domain.listing.ListingOutcome expire(ListingId listingId,
                CharacterId characterId, Instant now) {
            throw new AssertionError("asking must not close anything");
        }

        @Override
        public Optional<LfgListing> findListing(ListingId listingId) {
            return Optional.ofNullable(listings.get(listingId));
        }

        @Override
        public Optional<JoinRequest> findJoinRequest(JoinRequestId joinRequestId) {
            return requests.stream().filter(request -> request.id().equals(joinRequestId)).findFirst();
        }

        @Override
        public void save(JoinRequest request) {
            requests.add(request);
        }

        @Override
        public boolean hasPendingRequest(PlayerId ownerId, CharacterId requesterId,
                                         UUID listingId) {
            return requests.stream()
                    .filter(JoinRequest::isPending)
                    .anyMatch(request -> request.listingId().value().equals(listingId));
        }

        @Override
        public int pendingRequestCount(PlayerId ownerId, Instant now) {
            return (int) requests.stream()
                    .filter(JoinRequest::isPending)
                    .filter(request -> request.expiresAt().isAfter(now))
                    .count();
        }
    }
}
