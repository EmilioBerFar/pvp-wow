package com.pvpwow.lfg.adapters.in.rest;

import com.pvpwow.lfg.application.port.in.RegisterCharacter;
import com.pvpwow.lfg.application.port.out.Characters;
import com.pvpwow.lfg.application.port.out.CurrentPlayerProvider;
import com.pvpwow.lfg.application.port.out.Seasons;
import com.pvpwow.lfg.application.service.RegisterCharacterService;
import com.pvpwow.lfg.domain.character.Bracket;
import com.pvpwow.lfg.domain.character.Character;
import com.pvpwow.lfg.domain.character.CharacterId;
import com.pvpwow.lfg.domain.character.CharacterName;
import com.pvpwow.lfg.domain.character.ItemLevel;
import com.pvpwow.lfg.domain.character.PvpSeason;
import com.pvpwow.lfg.domain.character.Rating;
import com.pvpwow.lfg.domain.character.Realm;
import com.pvpwow.lfg.domain.character.Region;
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
import org.springframework.test.web.servlet.MvcResult;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Web slice of the registration flow: the REAL service, hand-wired on
 * hand-rolled fakes with a frozen clock — no Mockito, no database, no full
 * Spring context. What this proves is the whole inbound path: JSON →
 * command → use case → 201/422.
 */
@WebMvcTest(RegisterCharacterController.class)
@Import(RegisterCharacterControllerTest.FakeWiring.class)
class RegisterCharacterControllerTest {

    private static final Instant NOW = Instant.parse("2026-11-01T10:00:00Z");
    private static final PlayerId PLAYER = new PlayerId(UUID.randomUUID());

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private Characters characters;

    @BeforeEach
    void resetTheFake() {
        ((FakeCharacters) characters).reset();
    }

    @Test
    void registersACharacterAnswers201AndStampsTheSelfReport() throws Exception {
        MvcResult result = mockMvc.perform(post("/api/characters")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "Frostbolt",
                                  "realm": "Sanguino",
                                  "region": "EU",
                                  "wowClass": "MAGE",
                                  "specs": ["MAGE_FROST"],
                                  "itemLevel": 675,
                                  "ratings": {"2v2": 1800}
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").exists())
                .andReturn();

        Character saved = ((FakeCharacters) characters).onlySaved();
        assertThat(result.getResponse().getHeader("Location"))
                .isEqualTo("/api/characters/" + saved.id().value());
        assertThat(saved.ownerId()).isEqualTo(PLAYER);
        assertThat(saved.gear().itemLevel()).isEqualTo(new ItemLevel(675));
        assertThat(saved.gear().observedAt()).isEqualTo(NOW);
        assertThat(saved.standingFor(Bracket.TWO_VS_TWO).orElseThrow().rating())
                .isEqualTo(new Rating(1800));
    }

    @Test
    void anInvalidDeclaredFactIsA422CarryingTheDomainMessage() throws Exception {
        // "Frostb0lt": a digit — names are 2-12 LETTERS. Blizzard allows
        // two-letter names, so the earlier "Ab" version of this test was
        // wrong about the rule (it created a character and got a 201).
        mockMvc.perform(post("/api/characters")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "Frostb0lt",
                                  "realm": "Sanguino",
                                  "region": "EU",
                                  "wowClass": "MAGE",
                                  "specs": ["MAGE_FROST"],
                                  "itemLevel": 675
                                }
                                """))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.detail").value(
                        "Character name must be 2-12 letters, got: 'Frostb0lt'"));
    }

    @Test
    void anUnknownBracketIsA422NamingTheValidOnes() throws Exception {
        mockMvc.perform(post("/api/characters")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "Frostbolt",
                                  "realm": "Sanguino",
                                  "region": "EU",
                                  "wowClass": "MAGE",
                                  "specs": ["MAGE_FROST"],
                                  "itemLevel": 675,
                                  "ratings": {"5v5": 1800}
                                }
                                """))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.detail").value("Unknown bracket '5v5' — valid: 2v2, 3v3"));
    }

    @Test
    void aDuplicateIsA422() throws Exception {
        String body = """
                {
                  "name": "Frostbolt",
                  "realm": "Sanguino",
                  "region": "EU",
                  "wowClass": "MAGE",
                  "specs": ["MAGE_FROST"],
                  "itemLevel": 675
                }
                """;
        mockMvc.perform(post("/api/characters")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/characters")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.detail").value(
                        "Character Frostbolt on realm sanguino (EU) is already registered"));
    }

    @Test
    void anInvalidObservedFactIsA422Too() throws Exception {
        mockMvc.perform(post("/api/characters")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "Frostbolt",
                                  "realm": "Sanguino",
                                  "region": "EU",
                                  "wowClass": "MAGE",
                                  "specs": ["MAGE_FROST"],
                                  "itemLevel": 0
                                }
                                """))
                .andExpect(status().isUnprocessableEntity());
    }

    @TestConfiguration
    static class FakeWiring {

        @Bean
        Characters characters() {
            return new FakeCharacters();
        }

        @Bean
        CurrentPlayerProvider currentPlayer() {
            return () -> PLAYER;
        }

        @Bean
        Seasons seasons() {
            return () -> new PvpSeason(42);
        }

        @Bean
        Clock clock() {
            return Clock.fixed(NOW, ZoneOffset.UTC);
        }

        @Bean
        RegisterCharacter registerCharacter(Characters characters,
                                            CurrentPlayerProvider currentPlayer,
                                            Seasons seasons,
                                            Clock clock) {
            return new RegisterCharacterService(characters, currentPlayer, seasons, clock);
        }
    }

    private static final class FakeCharacters implements Characters {

        private final Set<String> registered = new HashSet<>();
        private final List<Character> saved = new ArrayList<>();

        @Override
        public void save(Character character) {
            saved.add(character);
            registered.add(key(character.region(), character.realm(), character.name()));
        }

        @Override
        public Optional<Character> findById(CharacterId id) {
            return saved.stream().filter(character -> character.id().equals(id)).findFirst();
        }

        @Override
        public boolean alreadyRegistered(Region region, Realm realm, CharacterName name) {
            return registered.contains(key(region, realm, name));
        }

        void reset() {
            registered.clear();
            saved.clear();
        }

        Character onlySaved() {
            assertThat(saved).hasSize(1);
            return saved.get(0);
        }

        private String key(Region region, Realm realm, CharacterName name) {
            return "%s|%s|%s".formatted(region, realm.value(), name.value());
        }
    }
}
