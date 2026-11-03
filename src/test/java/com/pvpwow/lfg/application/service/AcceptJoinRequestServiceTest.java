package com.pvpwow.lfg.application.service;

import com.pvpwow.lfg.application.error.ResourceNotFoundException;
import com.pvpwow.lfg.application.port.out.Characters;
import com.pvpwow.lfg.application.port.out.CurrentPlayerProvider;
import com.pvpwow.lfg.application.port.out.ListingLifecycle;
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
import com.pvpwow.lfg.domain.join.JoinRequest;
import com.pvpwow.lfg.domain.join.JoinRequestId;
import com.pvpwow.lfg.domain.join.JoinRequestStatus;
import com.pvpwow.lfg.domain.listing.AvailabilitySchedule;
import com.pvpwow.lfg.domain.listing.LfgListing;
import com.pvpwow.lfg.domain.listing.Language;
import com.pvpwow.lfg.domain.listing.ListingId;
import com.pvpwow.lfg.domain.listing.ListingOutcome;
import com.pvpwow.lfg.domain.listing.TimeWindow;
import com.pvpwow.lfg.domain.player.PlayerId;
import com.pvpwow.lfg.domain.shared.DomainRuleViolationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.DayOfWeek;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The rules that make COMPLETED mean something: only the owner accepts, and
 * nobody accepts their own listing.
 */
class AcceptJoinRequestServiceTest {

    private static final Instant NOW = Instant.parse("2026-11-03T10:00:00Z");
    private static final PlayerId OWNER = new PlayerId(UUID.randomUUID());
    private static final PlayerId REQUESTER_OWNER = new PlayerId(UUID.randomUUID());
    private static final PlayerId STRANGER = new PlayerId(UUID.randomUUID());
    private static final CharacterId OWNER_CHARACTER = new CharacterId(UUID.randomUUID());
    private static final CharacterId REQUESTER_CHARACTER = new CharacterId(UUID.randomUUID());
    private static final CharacterId STRANGER_CHARACTER = new CharacterId(UUID.randomUUID());
    private static final Duration TTL = Duration.ofHours(24);

    private final FakeCharacters characters = new FakeCharacters();
    private final FakeLifecycle lifecycle = new FakeLifecycle();
    private final AcceptJoinRequestService service = new AcceptJoinRequestService(characters,
            lifecycle, () -> OWNER, Clock.fixed(NOW, ZoneOffset.UTC));

    @BeforeEach
    void reset() {
        characters.reset();
        lifecycle.reset();
        characters.store(character(OWNER_CHARACTER, OWNER));
        characters.store(character(REQUESTER_CHARACTER, REQUESTER_OWNER));
        characters.store(character(STRANGER_CHARACTER, STRANGER));
    }

    @Test
    void acceptingClosesEveryActiveListingOfBothPlayersAndWritesTheirFacts() {
        LfgListing ownerListing = publish(OWNER_CHARACTER);
        LfgListing requesterListing = publish(REQUESTER_CHARACTER);
        lifecycle.storeListing(ownerListing);
        lifecycle.storeListing(requesterListing);
        JoinRequest request = pendingRequest(REQUESTER_CHARACTER, ownerListing);

        service.accept(request.id());

        assertThat(lifecycle.closedBy(OWNER_CHARACTER))
                .containsExactly(ownerListing.id());
        assertThat(lifecycle.closedBy(REQUESTER_CHARACTER))
                .containsExactly(requesterListing.id());
        assertThat(lifecycle.facts())
                .hasSize(2)
                .allSatisfy(fact ->
                        assertThat(fact.kind())
                                .isEqualTo(com.pvpwow.lfg.domain.listing.ListingOutcomeKind.COMPLETED));
        assertThat(lifecycle.request(request.id()).status())
                .isEqualTo(JoinRequestStatus.ACCEPTED);
    }

    @Test
    void acceptingClosesEveryListingOfBothPlayersNotOnlyTheOneRequested() {
        LfgListing threeVThree = publish(OWNER_CHARACTER, Bracket.THREE_VS_THREE);
        LfgListing twoVTwo = publish(OWNER_CHARACTER, Bracket.TWO_VS_TWO);
        lifecycle.storeListing(threeVThree);
        lifecycle.storeListing(twoVTwo);
        JoinRequest request = pendingRequest(REQUESTER_CHARACTER, threeVThree);

        service.accept(request.id());

        assertThat(lifecycle.closedBy(OWNER_CHARACTER))
                .containsExactlyInAnyOrder(threeVThree.id(), twoVTwo.id());
    }

    @Test
    void aPlayerWithoutAListingStillClosesTheOwnersListings() {
        LfgListing ownerListing = publish(OWNER_CHARACTER);
        lifecycle.storeListing(ownerListing);
        JoinRequest request = pendingRequest(REQUESTER_CHARACTER, ownerListing);

        service.accept(request.id());

        assertThat(lifecycle.closedBy(OWNER_CHARACTER)).containsExactly(ownerListing.id());
        assertThat(lifecycle.closedBy(REQUESTER_CHARACTER)).isEmpty();
        assertThat(lifecycle.facts()).hasSize(1);
    }

    @Test
    void theListingsOfSomebodyElseAreNotTouched() {
        LfgListing ownerListing = publish(OWNER_CHARACTER);
        LfgListing strangerListing = publish(STRANGER_CHARACTER);
        lifecycle.storeListing(ownerListing);
        lifecycle.storeListing(strangerListing);
        JoinRequest request = pendingRequest(REQUESTER_CHARACTER, ownerListing);

        service.accept(request.id());

        assertThat(lifecycle.closedBy(STRANGER_CHARACTER))
                .as("only the owner and the requester close")
                .isEmpty();
        assertThat(lifecycle.facts())
                .extracting(fact -> fact.listingId())
                .doesNotContain(strangerListing.id());
    }

    @Test
    void acceptingYourOwnListingIsRejected() {
        LfgListing ownerListing = publish(OWNER_CHARACTER);
        lifecycle.storeListing(ownerListing);
        JoinRequest request = pendingRequest(OWNER_CHARACTER, ownerListing);

        assertThatThrownBy(() -> service.accept(request.id()))
                .isInstanceOf(DomainRuleViolationException.class)
                .hasMessageContaining("cannot accept your own listing");
        assertThat(lifecycle.closed)
                .as("nothing may close")
                .isEmpty();
    }

    @Test
    void acceptingARequestYouDoNotOwnIsTheSame404AsAnAbsentOne() {
        LfgListing ownerListing = publish(OWNER_CHARACTER);
        lifecycle.storeListing(ownerListing);
        JoinRequest request = pendingRequest(REQUESTER_CHARACTER, ownerListing);

        assertThatThrownBy(() -> service.accept(new JoinRequestId(UUID.randomUUID())))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("Join request not found");
    }

    @Test
    void acceptingARequestThatIsNoLongerPendingIsRejected() {
        LfgListing ownerListing = publish(OWNER_CHARACTER);
        lifecycle.storeListing(ownerListing);
        JoinRequest request = pendingRequest(REQUESTER_CHARACTER, ownerListing);
        request.reject();

        assertThatThrownBy(() -> service.accept(request.id()))
                .isInstanceOf(DomainRuleViolationException.class)
                .hasMessageContaining("no longer pending");
    }

    @Test
    void acceptingCancelsEveryPendingRequestOfTheRequestersPlayer() {
        LfgListing ownerListing = publish(OWNER_CHARACTER);
        LfgListing otherListing = publish(OWNER_CHARACTER, Bracket.TWO_VS_TWO);
        lifecycle.storeListing(ownerListing);
        lifecycle.storeListing(otherListing);
        JoinRequest accepted = pendingRequest(REQUESTER_CHARACTER, ownerListing);
        JoinRequest other = pendingRequest(REQUESTER_CHARACTER, otherListing);

        service.accept(accepted.id());

        assertThat(other.status())
                .as("starting to play closes every pending request of the player")
                .isEqualTo(JoinRequestStatus.CANCELLED);
    }

    private JoinRequest pendingRequest(CharacterId requester, LfgListing listing) {
        JoinRequest request = JoinRequest.request(requester, listing.id(), NOW,
                listing.expiresAt());
        lifecycle.storeRequest(request);
        return request;
    }

    private LfgListing publish(CharacterId characterId) {
        return publish(characterId, Bracket.THREE_VS_THREE);
    }

    private LfgListing publish(CharacterId characterId, Bracket bracket) {
        return LfgListing.publish(characterId, bracket, Specialization.MAGE_FROST,
                Set.of(Language.ES),
                new AvailabilitySchedule(
                        Set.of(new TimeWindow(DayOfWeek.MONDAY, LocalTime.of(18, 0),
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

        private final Map<CharacterId, Character> stored = new HashMap<>();

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

        private final Map<ListingId, LfgListing> listings = new HashMap<>();
        private final List<JoinRequest> requests = new ArrayList<>();
        private final List<ListingId> closed = new ArrayList<>();
        private final List<ListingOutcome> facts = new ArrayList<>();

        void storeListing(LfgListing listing) {
            listings.put(listing.id(), listing);
        }

        void storeRequest(JoinRequest request) {
            requests.add(request);
        }

        void reset() {
            listings.clear();
            requests.clear();
            closed.clear();
            facts.clear();
        }

        List<ListingId> closedBy(CharacterId characterId) {
            return closed.stream()
                    .filter(listingId -> listings.get(listingId).characterId().equals(characterId))
                    .toList();
        }

        List<ListingOutcome> facts() {
            return List.copyOf(facts);
        }

        JoinRequest request(JoinRequestId id) {
            return requests.stream().filter(request -> request.id().equals(id)).findFirst()
                    .orElseThrow();
        }

        @Override
        public List<ListingOutcome> completeGroup(JoinRequest acceptedRequest,
                                                  CharacterId ownerId, CharacterId requesterId,
                                                  PlayerId requesterOwnerId, Instant now) {
            for (CharacterId characterId : List.of(ownerId, requesterId)) {
                for (LfgListing listing : listings.values()) {
                    if (listing.characterId().equals(characterId)
                            && listing.status() == com.pvpwow.lfg.domain.listing.ListingStatus.ACTIVE) {
                        listing.complete();
                        closed.add(listing.id());
                        facts.add(new ListingOutcome(listing.id(), characterId,
                                listing.bracket(), listing.spec(),
                                com.pvpwow.lfg.domain.listing.ListingOutcomeKind.COMPLETED,
                                listing.createdAt(), now));
                    }
                }
            }
            for (JoinRequest request : requests) {
                if (request != acceptedRequest && request.isPending()
                        && request.requesterId().equals(requesterId)) {
                    request.cancel();
                }
            }
            acceptedRequest.accept();
            return List.copyOf(facts);
        }

        @Override
        public ListingOutcome withdraw(ListingId listingId, CharacterId characterId, Instant now) {
            throw new AssertionError("not used here");
        }

        @Override
        public ListingOutcome expire(ListingId listingId, CharacterId characterId, Instant now) {
            throw new AssertionError("not used here");
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
            return false;
        }

        @Override
        public int pendingRequestCount(PlayerId ownerId) {
            return 0;
        }
    }
}
