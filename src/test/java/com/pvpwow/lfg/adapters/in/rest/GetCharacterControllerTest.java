package com.pvpwow.lfg.adapters.in.rest;

import com.jayway.jsonpath.JsonPath;
import com.pvpwow.lfg.application.port.in.GetCharacter;
import com.pvpwow.lfg.application.port.out.Characters;
import com.pvpwow.lfg.application.port.out.CurrentPlayerProvider;
import com.pvpwow.lfg.application.service.GetCharacterService;
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
import com.pvpwow.lfg.domain.character.Region;
import com.pvpwow.lfg.domain.character.Specialization;
import com.pvpwow.lfg.domain.character.WowClass;
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
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Web slice of the character read. The anti-enumeration rule is asserted on
 * the actual response bodies: an unknown id, another player's character and a
 * malformed id must be byte-for-byte the same answer.
 */
@WebMvcTest({GetCharacterController.class, PublishListingController.class})
@Import(GetCharacterControllerTest.FakeWiring.class)
class GetCharacterControllerTest {

    private static final Instant NOW = Instant.parse("2026-11-02T10:00:00Z");
    private static final PlayerId OWNER = new PlayerId(UUID.randomUUID());
    private static final CharacterId CHARACTER_ID = new CharacterId(UUID.randomUUID());

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private Characters characters;

    @BeforeEach
    void resetTheFake() {
        ((FakeCharacters) characters).reset();
    }

    @Test
    void returnsTheCallersOwnCharacterWithEveryBracketStanding() throws Exception {
        fake().store(characterOwnedBy(OWNER));

        mockMvc.perform(get("/api/characters/{id}", CHARACTER_ID.value()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(CHARACTER_ID.value().toString()))
                .andExpect(jsonPath("$.name").value("Frostbolt"))
                .andExpect(jsonPath("$.realm").value("sanguino"))
                .andExpect(jsonPath("$.region").value("EU"))
                .andExpect(jsonPath("$.wowClass").value("MAGE"))
                .andExpect(jsonPath("$.specs[0]").value("MAGE_FROST"))
                .andExpect(jsonPath("$.itemLevel").value(675))
                .andExpect(jsonPath("$.gearObservedAt").exists())
                .andExpect(jsonPath("$.standings['2v2'].rating").value(1800))
                .andExpect(jsonPath("$.standings['2v2'].season").value(42))
                .andExpect(jsonPath("$.standings['3v3'].rating").value(2100))
                .andExpect(jsonPath("$.ownerId").doesNotExist());
    }

    @Test
    void anUnknownIdAndAnotherPlayersCharacterReturnTheSameBody() throws Exception {
        Map<String, Object> unknown = notFoundProblem(UUID.randomUUID().toString());

        fake().store(characterOwnedBy(new PlayerId(UUID.randomUUID())));
        Map<String, Object> foreign = notFoundProblem(CHARACTER_ID.value().toString());

        assertThat(foreign).isEqualTo(unknown);
        assertThat(unknown).containsEntry("detail", "Character not found");
        assertThat(unknown.toString()).doesNotContain("Frostbolt", "sanguino", "EU");
    }

    @Test
    void aSyntacticallyBrokenBodyIsA400NotA422() throws Exception {
        mockMvc.perform(post("/api/listings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"characterId\": "))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Malformed request body"));
    }

    /**
     * The malformed body is a 400, not a 404 and not a 422: the request never
     * became an object, so there is no field to blame. The advice's generic
     * handler answers 400 with a fixed phrase and no internals.
     */
    @Test
    void aMalformedBodyNeverLeaksTheParser() throws Exception {
        mockMvc.perform(post("/api/listings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"characterId\": \"x\",}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Malformed request body"))
                .andExpect(jsonPath("$.properties").doesNotExist());
    }

    @Test
    void aMalformedIdIsTheSameNotFound() throws Exception {
        Map<String, Object> malformed = notFoundProblem("not-a-uuid");
        Map<String, Object> unknown = notFoundProblem(UUID.randomUUID().toString());

        assertThat(malformed).isEqualTo(unknown);
    }

    /**
     * The problem body minus {@code instance}: ProblemDetail echoes the request
     * URI there, and that URI is the caller's own input, not character data.
     * Everything else — type, title, status, detail and the absence of extra
     * properties — must be identical across all three not-found paths.
     */
    private Map<String, Object> notFoundProblem(String id) throws Exception {
        String body = mockMvc.perform(get("/api/characters/{id}", id))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value("Character not found"))
                .andReturn().getResponse().getContentAsString();
        Map<String, Object> problem = JsonPath.read(body, "$");
        problem.remove("instance");
        return problem;
    }

    private FakeCharacters fake() {
        return (FakeCharacters) characters;
    }

    private static Character characterOwnedBy(PlayerId owner) {
        return Character.rehydrate(CHARACTER_ID, owner, new CharacterName("Frostbolt"),
                new Realm("sanguino"), Region.EU, WowClass.MAGE,
                new GearSnapshot(new ItemLevel(675), NOW),
                Set.of(Specialization.MAGE_FROST),
                Map.of(Bracket.TWO_VS_TWO, new PvpStanding(new Rating(1800), new PvpSeason(42), NOW),
                        Bracket.THREE_VS_THREE,
                        new PvpStanding(new Rating(2100), new PvpSeason(42), NOW)));
    }

    @TestConfiguration
    static class FakeWiring {

        @Bean
        Characters characters() {
            return new FakeCharacters();
        }

        @Bean
        CurrentPlayerProvider currentPlayer() {
            return () -> OWNER;
        }

        @Bean
        GetCharacter getCharacter(Characters characters, CurrentPlayerProvider currentPlayer) {
            return new GetCharacterService(characters, currentPlayer);
        }

        /**
         * The publication endpoint is loaded too, only so the malformed-body
         * contract can be proven on a real POST body. It never reaches the
         * use case: the parser rejects the body first.
         */
        @Bean
        com.pvpwow.lfg.application.port.in.PublishListing publishListing(
                Characters characters, CurrentPlayerProvider currentPlayer) {
            return new com.pvpwow.lfg.application.service.PublishListingService(
                    characters,
                    new NoopListings(),
                    currentPlayer,
                    Clock.fixed(NOW, ZoneOffset.UTC),
                    java.time.Duration.ofHours(24));
        }
    }

    private static final class NoopListings
            implements com.pvpwow.lfg.application.port.out.Listings {

        @Override
        public void publish(com.pvpwow.lfg.domain.listing.LfgListing listing) {
            throw new AssertionError("a malformed body must never reach the use case");
        }

        @Override
        public boolean hasActiveFor(
                com.pvpwow.lfg.domain.character.CharacterId characterId,
                com.pvpwow.lfg.domain.character.Bracket bracket,
                java.time.Instant now) {
            throw new AssertionError("a malformed body must never reach the use case");
        }
    }

    private static final class FakeCharacters implements Characters {

        private Character stored;

        void store(Character character) {
            stored = character;
        }

        void reset() {
            stored = null;
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
}
