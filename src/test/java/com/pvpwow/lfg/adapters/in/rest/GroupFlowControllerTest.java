package com.pvpwow.lfg.adapters.in.rest;

import com.pvpwow.lfg.application.port.in.AcceptJoinRequest;
import com.pvpwow.lfg.application.port.in.RejectJoinRequest;
import com.pvpwow.lfg.application.port.in.RequestToJoin;
import com.pvpwow.lfg.application.port.in.WithdrawJoinRequest;
import com.pvpwow.lfg.application.port.in.WithdrawListing;
import com.pvpwow.lfg.application.port.out.Characters;
import com.pvpwow.lfg.application.port.out.CurrentPlayerProvider;
import com.pvpwow.lfg.application.port.out.ListingLifecycle;
import com.pvpwow.lfg.application.service.AcceptJoinRequestService;
import com.pvpwow.lfg.application.service.RejectJoinRequestService;
import com.pvpwow.lfg.application.service.RequestToJoinService;
import com.pvpwow.lfg.application.service.WithdrawJoinRequestService;
import com.pvpwow.lfg.application.service.WithdrawListingService;
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
import com.pvpwow.lfg.domain.listing.AvailabilitySchedule;
import com.pvpwow.lfg.domain.listing.LfgListing;
import com.pvpwow.lfg.domain.listing.ListingId;
import com.pvpwow.lfg.domain.listing.ListingOutcome;
import com.pvpwow.lfg.domain.listing.TimeWindow;
import com.pvpwow.lfg.domain.player.PlayerId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Web slice of the group handshake: the REAL services on hand-rolled fakes.
 * It proves the wire contract of the five endpoints and the 404s.
 */
@WebMvcTest({RequestToJoinController.class, AcceptJoinRequestController.class,
        RejectJoinRequestController.class, WithdrawJoinRequestController.class,
        WithdrawListingController.class})
@Import(GroupFlowControllerTest.FakeWiring.class)
class GroupFlowControllerTest {

    private static final Instant NOW = Instant.parse("2026-11-03T10:00:00Z");
    private static final Duration TTL = Duration.ofHours(24);
    private static final PlayerId CALLER = new PlayerId(UUID.randomUUID());
    private static final PlayerId OTHER = new PlayerId(UUID.randomUUID());
    private static final CharacterId CHARACTER = new CharacterId(UUID.randomUUID());
    private static final CharacterId OTHER_CHARACTER = new CharacterId(UUID.randomUUID());
    private static final AvailabilitySchedule SCHEDULE = new AvailabilitySchedule(
            Set.of(new TimeWindow(DayOfWeek.MONDAY, LocalTime.of(18, 0), LocalTime.of(22, 0))),
            ZoneId.of("Europe/Madrid"));

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private Characters characters;

    @Autowired
    private ListingLifecycle lifecycle;

    @BeforeEach
    void resetTheFakes() {
        ((FakeCharacters) characters).reset();
        ((FakeLifecycle) lifecycle).reset();
        ((FakeCharacters) characters).store(character(CHARACTER, CALLER));
        ((FakeCharacters) characters).store(character(OTHER_CHARACTER, OTHER));
    }

    @Test
    void askingToJoinAnswers201WithTheRequestId() throws Exception {
        LfgListing listing = ((FakeLifecycle) lifecycle).storeListing(character(OTHER_CHARACTER, OTHER));

        mockMvc.perform(post("/api/listings/{id}/join-requests", listing.id().value())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"characterId\": \"%s\"}".formatted(CHARACTER.value())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").exists());
    }

    @Test
    void askingForYourOwnListingIsA422() throws Exception {
        LfgListing listing = ((FakeLifecycle) lifecycle).storeListing(character(CHARACTER, CALLER));

        mockMvc.perform(post("/api/listings/{id}/join-requests", listing.id().value())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"characterId\": \"%s\"}".formatted(CHARACTER.value())))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.detail").value(
                        "You cannot request to join your own listing"));
    }

    @Test
    void aMissingCharacterIdIsA422() throws Exception {
        LfgListing listing = ((FakeLifecycle) lifecycle).storeListing(character(CHARACTER, CALLER));

        mockMvc.perform(post("/api/listings/{id}/join-requests", listing.id().value())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    void aMalformedIdentifierIsA400() throws Exception {
        mockMvc.perform(post("/api/listings/not-a-uuid/join-requests")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"characterId\": \"%s\"}".formatted(CHARACTER.value())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Malformed identifier"));
    }

    @Test
    void acceptingARequestAnswers204() throws Exception {
        LfgListing listing = ((FakeLifecycle) lifecycle).storeListing(character(CHARACTER, CALLER));
        JoinRequest request = ((FakeLifecycle) lifecycle).storeRequest(
                OTHER_CHARACTER, listing.id());

        mockMvc.perform(post("/api/join-requests/{id}/accept", request.id().value()))
                .andExpect(status().isNoContent());
    }

    @Test
    void rejectingARequestAnswers204() throws Exception {
        LfgListing listing = ((FakeLifecycle) lifecycle).storeListing(character(CHARACTER, CALLER));
        JoinRequest request = ((FakeLifecycle) lifecycle).storeRequest(
                OTHER_CHARACTER, listing.id());

        mockMvc.perform(post("/api/join-requests/{id}/reject", request.id().value()))
                .andExpect(status().isNoContent());
    }

    @Test
    void withdrawingARequestAnswers204() throws Exception {
        JoinRequest request = ((FakeLifecycle) lifecycle).storeRequest(
                CHARACTER, new ListingId(UUID.randomUUID()));

        mockMvc.perform(post("/api/join-requests/{id}/withdraw", request.id().value()))
                .andExpect(status().isNoContent());
    }

    @Test
    void withdrawingAListingAnswers204() throws Exception {
        LfgListing listing = ((FakeLifecycle) lifecycle).storeListing(character(CHARACTER, CALLER));

        mockMvc.perform(delete("/api/listings/{id}", listing.id().value()))
                .andExpect(status().isNoContent());
    }

    @Test
    void anAbsentJoinRequestIs404() throws Exception {
        mockMvc.perform(post("/api/join-requests/{id}/accept", UUID.randomUUID()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value("Join request not found"));
    }

    private static Character character(CharacterId id, PlayerId owner) {
        return Character.rehydrate(id, owner, new CharacterName("Frostbolt"),
                new Realm("sanguino"), Region.EU, WowClass.MAGE,
                new GearSnapshot(new ItemLevel(675), NOW),
                Set.of(Specialization.MAGE_FROST), Map.of());
    }

    @TestConfiguration
    static class FakeWiring {

        @Bean
        Characters characters() {
            return new FakeCharacters();
        }

        @Bean
        ListingLifecycle lifecycle() {
            return new FakeLifecycle();
        }

        @Bean
        CurrentPlayerProvider currentPlayer() {
            return () -> CALLER;
        }

        @Bean
        Clock clock() {
            return Clock.fixed(NOW, ZoneOffset.UTC);
        }

        @Bean
        RequestToJoin requestToJoin(Characters characters, ListingLifecycle lifecycle,
                                    CurrentPlayerProvider currentPlayer, Clock clock) {
            return new RequestToJoinService(characters, lifecycle, currentPlayer, clock);
        }

        @Bean
        AcceptJoinRequest acceptJoinRequest(Characters characters, ListingLifecycle lifecycle,
                                            CurrentPlayerProvider currentPlayer, Clock clock) {
            return new AcceptJoinRequestService(characters, lifecycle, currentPlayer, clock);
        }

        @Bean
        RejectJoinRequest rejectJoinRequest(Characters characters, ListingLifecycle lifecycle,
                                            CurrentPlayerProvider currentPlayer) {
            return new RejectJoinRequestService(characters, lifecycle, currentPlayer);
        }

        @Bean
        WithdrawJoinRequest withdrawJoinRequest(Characters characters, ListingLifecycle lifecycle,
                                                CurrentPlayerProvider currentPlayer) {
            return new WithdrawJoinRequestService(characters, lifecycle, currentPlayer);
        }

        @Bean
        WithdrawListing withdrawListing(Characters characters, ListingLifecycle lifecycle,
                                        CurrentPlayerProvider currentPlayer, Clock clock) {
            return new WithdrawListingService(characters, lifecycle, currentPlayer, clock);
        }
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

        LfgListing storeListing(Character character) {
            LfgListing listing = LfgListing.publish(character.id(),
                    com.pvpwow.lfg.domain.character.Bracket.THREE_VS_THREE,
                    Specialization.MAGE_FROST, Set.of(
                            com.pvpwow.lfg.domain.listing.Language.ES), SCHEDULE, NOW, TTL);
            listings.put(listing.id(), listing);
            return listing;
        }

        JoinRequest storeRequest(CharacterId requesterId, ListingId listingId) {
            JoinRequest request = JoinRequest.request(requesterId, listingId, NOW,
                    NOW.plus(TTL));
            requests.add(request);
            return request;
        }

        void reset() {
            listings.clear();
            requests.clear();
        }

        @Override
        public List<ListingOutcome> completeGroup(JoinRequest acceptedRequest,
                                                  CharacterId ownerId, CharacterId requesterId,
                                                  PlayerId ownerPlayerId,
                                                  PlayerId requesterOwnerId, Instant now) {
            acceptedRequest.accept();
            return List.of();
        }

        @Override
        public ListingOutcome withdraw(ListingId listingId, CharacterId characterId, Instant now) {
            return null;
        }

        @Override
        public ListingOutcome expire(ListingId listingId, CharacterId characterId, Instant now) {
            return null;
        }

        @Override
        public Optional<LfgListing> findListing(ListingId listingId) {
            return Optional.ofNullable(listings.get(listingId));
        }

        @Override
        public Optional<JoinRequest> findJoinRequest(JoinRequestId joinRequestId) {
            return requests.stream()
                    .filter(request -> request.id().equals(joinRequestId))
                    .findFirst();
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
        public int pendingRequestCount(PlayerId ownerId, Instant now) {
            return 0;
        }
    }
}
