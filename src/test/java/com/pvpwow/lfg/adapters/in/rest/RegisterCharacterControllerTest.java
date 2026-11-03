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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
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
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Stream;

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

    /**
     * Same rule as the publication DTO: every required field is a 422 when it
     * is missing (a JSON null binds exactly like an absent field), and none of
     * them may reach a null guard and surface as a 500.
     */
    @ParameterizedTest(name = "a missing {0} is a 422")
    @MethodSource("requiredFields")
    void aMissingRequiredFieldIsA422(String field, String body, String detail) throws Exception {
        mockMvc.perform(post("/api/characters")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.detail").value(detail));
    }

    /**
     * The declaration is the contract: every field the DTO declares as
     * required carries a presence constraint. A field added to the record
     * without one fails HERE, not in production.
     *
     * <p>The optional fields are NOT excluded generically: they are listed
     * one by one, each with the reason it may be absent. A new optional field
     * must be added to that list with its reason, or this test fails.
     */
    @Test
    void everyFieldOfTheRequestIsDeclaredRequiredExceptTheListedOnes() {
        assertThat(requiredFieldsOf(RegisterCharacterRequest.class))
                .containsExactlyInAnyOrderElementsOf(
                        allFieldNames(RegisterCharacterRequest.class)
                                .stream()
                                .filter(name -> !OPTIONAL_FIELDS.containsKey(name))
                                .toList());
    }

    /**
     * The optional fields, each with the reason it may be absent. An empty
     * reason would be a smell: an optional field needs a PRODUCT reason, not
     * a technical one.
     */
    private static final Map<String, String> OPTIONAL_FIELDS = Map.of(
            "ratings", """
                    a character may be registered unranked: the ratings are a
                    self-report and an unranked bracket is a legitimate state,
                    not a malformed request""");

    /**
     * The elements of a collection of scalars carry their own presence
     * constraint: {@code Set<@NotNull @NotBlank String>} rejects a null entry
     * even though the set itself is present.
     */
    @Test
    void theElementsOfASetAreConstrainedToo() {
        assertThat(elementTypeIsRequired(RegisterCharacterRequest.class, "specs")).isTrue();
    }

    private static boolean elementTypeIsRequired(Class<?> owner, String fieldName) {
        java.lang.reflect.AnnotatedType type = java.util.Arrays.stream(
                        owner.getRecordComponents())
                .filter(component -> component.getName().equals(fieldName))
                .findFirst()
                .orElseThrow()
                .getAnnotatedType();
        java.lang.reflect.AnnotatedType[] arguments =
                ((java.lang.reflect.AnnotatedParameterizedType) type).getAnnotatedActualTypeArguments();
        return java.util.Arrays.stream(arguments[0].getAnnotations())
                .anyMatch(annotation -> annotation.annotationType().getSimpleName()
                        .startsWith("Not"));
    }

    /**
     * {@code ratings} is the one OPTIONAL field, and on purpose: a character
     * may be registered unranked. Its CONTENTS are still constrained when it
     * is present.
     */
    @Test
    void ratingsIsOptionalButItsContentsAreConstrained() {
        assertThat(isRequired(RegisterCharacterRequest.class, "ratings")).isFalse();

        java.lang.reflect.AnnotatedType type = java.util.Arrays.stream(
                        RegisterCharacterRequest.class.getRecordComponents())
                .filter(component -> component.getName().equals("ratings"))
                .findFirst()
                .orElseThrow()
                .getAnnotatedType();
        assertThat(type.toString())
                .as("the map's key and value types carry the constraints")
                .contains("NotNull", "Min");
    }

    private static List<String> allFieldNames(Class<?> type) {
        return java.util.Arrays.stream(type.getRecordComponents())
                .map(java.lang.reflect.RecordComponent::getName)
                .toList();
    }

    /**
     * The annotations live on the backing FIELDS, not on the record
     * components: a record component is a constructor parameter, and Jakarta
     * Validation targets fields. Reading the field is what the validator does.
     */
    private static List<String> requiredFieldsOf(Class<?> type) {
        List<String> required = new ArrayList<>();
        for (java.lang.reflect.RecordComponent component : type.getRecordComponents()) {
            if (isRequired(type, component.getName())) {
                required.add(component.getName());
            }
        }
        return required;
    }

    private static boolean isRequired(Class<?> owner, String fieldName) {
        try {
            java.lang.reflect.Field field = owner.getDeclaredField(fieldName);
            return java.util.Arrays.stream(field.getAnnotations())
                    .anyMatch(annotation -> annotation.annotationType().getSimpleName()
                            .startsWith("Not"));
        } catch (NoSuchFieldException absent) {
            throw new AssertionError("no field " + fieldName + " in " + owner.getName());
        }
    }

    /**
     * The element type of a collection, read from the ANNOTATED type: the
     * presence constraints of {@code Set<@NotNull String>} live on the type
     * argument, and only the annotated type carries them.
     */
    private static Class<?> elementTypeOf(java.lang.reflect.AnnotatedType annotatedType) {
        if (annotatedType instanceof java.lang.reflect.AnnotatedParameterizedType parameterized) {
            java.lang.reflect.AnnotatedType[] arguments =
                    parameterized.getAnnotatedActualTypeArguments();
            if (arguments.length == 1 && arguments[0].getType() instanceof Class<?> element) {
                return element;
            }
        }
        return null;
    }

    private static Stream<Arguments> requiredFields() {
        return Stream.of(
                Arguments.of("name", body("null", REALM, REGION, CLASS, SPECS, ITEM_LEVEL),
                        "name is required"),
                Arguments.of("realm", body(NAME, "null", REGION, CLASS, SPECS, ITEM_LEVEL),
                        "realm is required"),
                Arguments.of("region", body(NAME, REALM, "null", CLASS, SPECS, ITEM_LEVEL),
                        "region is required"),
                Arguments.of("wowClass", body(NAME, REALM, REGION, "null", SPECS, ITEM_LEVEL),
                        "wowClass is required"),
                Arguments.of("itemLevel", body(NAME, REALM, REGION, CLASS, SPECS, "null"),
                        "itemLevel is required"),
                Arguments.of("specs", body(NAME, REALM, REGION, CLASS, "null", ITEM_LEVEL),
                        "specs is required"),
                Arguments.of("specs[] entries", body(NAME, REALM, REGION, CLASS, "[null]", ITEM_LEVEL),
                        "specs[] is required"));
    }

    private static final String NAME = "\"Frostbolt\"";
    private static final String REALM = "\"Sanguino\"";
    private static final String REGION = "\"EU\"";
    private static final String CLASS = "\"MAGE\"";
    private static final String SPECS = "[\"MAGE_FROST\"]";
    private static final String ITEM_LEVEL = "675";

    private static String body(String name, String realm, String region, String wowClass,
                               String specs, String itemLevel) {
        return """
                {
                  "name": %s,
                  "realm": %s,
                  "region": %s,
                  "wowClass": %s,
                  "specs": %s,
                  "itemLevel": %s
                }
                """.formatted(name, realm, region, wowClass, specs, itemLevel);
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
