package com.pvpwow.lfg.adapters.in.rest;

import com.pvpwow.lfg.application.port.in.PublishListing;
import com.pvpwow.lfg.application.port.out.Characters;
import com.pvpwow.lfg.application.port.out.CurrentPlayerProvider;
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
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Web slice of the publication flow: the REAL service, hand-wired on
 * hand-rolled fakes with a frozen clock. It proves JSON → command → use case →
 * 201/404/422, including that the client cannot negotiate the TTL and that the
 * 201 carries no Location pointing at a resource this API does not expose.
 */
@WebMvcTest(PublishListingController.class)
@Import(PublishListingControllerTest.FakeWiring.class)
class PublishListingControllerTest {

    private static final Instant NOW = Instant.parse("2026-11-02T10:00:00Z");
    private static final Duration TTL = Duration.ofHours(24);
    private static final PlayerId PLAYER = new PlayerId(UUID.randomUUID());
    private static final CharacterId CHARACTER_ID = new CharacterId(UUID.randomUUID());

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private Characters characters;

    @Autowired
    private Listings listings;

    @BeforeEach
    void resetTheFakes() {
        ((FakeCharacters) characters).reset();
        ((FakeListings) listings).reset();
        ((FakeCharacters) characters).store(characterOwnedBy(PLAYER));
    }

    @Test
    void publishesAValidListingAndAnswers201WithTheIdAndNoLocation() throws Exception {
        mockMvc.perform(post("/api/listings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request(CHARACTER_ID, "2v2", "MONDAY", "18:00", "22:00")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").exists())
                .andExpect(header().doesNotExist("Location"));

        LfgListing saved = ((FakeListings) listings).only();
        assertThat(saved.characterId()).isEqualTo(CHARACTER_ID);
        assertThat(saved.bracket()).isEqualTo(Bracket.TWO_VS_TWO);
        assertThat(saved.spec()).isEqualTo(Specialization.MAGE_FROST);
        assertThat(saved.languages()).containsExactlyInAnyOrder(Language.ES, Language.EN);
        assertThat(saved.schedule()).isEqualTo(new AvailabilitySchedule(
                Set.of(new TimeWindow(DayOfWeek.MONDAY, LocalTime.of(18, 0), LocalTime.of(22, 0))),
                ZoneId.of("Europe/Madrid")));
        assertThat(saved.createdAt()).isEqualTo(NOW);
        assertThat(saved.expiresAt()).isEqualTo(NOW.plus(TTL));
    }

    @Test
    void aDuplicateActiveListingIsA422() throws Exception {
        ((FakeListings) listings).alreadyActive = true;

        mockMvc.perform(post("/api/listings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request(CHARACTER_ID, "2v2", "MONDAY", "18:00", "22:00")))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.detail").value(
                        "Character Frostbolt already has an active TWO_VS_TWO listing"));
    }

    @Test
    void anUnknownBracketIsA422NamingTheValidOnes() throws Exception {
        mockMvc.perform(post("/api/listings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request(CHARACTER_ID, "5v5", "MONDAY", "18:00", "22:00")))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.detail").value("Unknown bracket '5v5' — valid: 2v2, 3v3"));
    }

    @Test
    void aWindowThatEndsBeforeItStartsIsA422() throws Exception {
        mockMvc.perform(post("/api/listings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request(CHARACTER_ID, "2v2", "MONDAY", "22:00", "18:00")))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.detail").value(
                        "MONDAY window must start before it ends, got: 22:00-18:00"));
    }

    @Test
    void anotherPlayersCharacterIsA404WithNoCharacterData() throws Exception {
        ((FakeCharacters) characters).store(characterOwnedBy(new PlayerId(UUID.randomUUID())));

        mockMvc.perform(post("/api/listings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request(CHARACTER_ID, "2v2", "MONDAY", "18:00", "22:00")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value("Character not found"))
                .andExpect(jsonPath("$.name").doesNotExist())
                .andExpect(jsonPath("$.realm").doesNotExist())
                .andExpect(jsonPath("$.region").doesNotExist());
    }

    /**
     * One case per required field, nested objects included. A JSON null and an
     * absent field both bind to a null record component, so each case proves
     * both. The rule is absolute: bad client input is a 422, and no missing
     * field may ever reach the command's null guard and become a 500.
     */
    @ParameterizedTest(name = "a missing {0} is a 422")
    @MethodSource("requiredFields")
    void aMissingRequiredFieldIsA422(String field, String body, String detail) throws Exception {
        mockMvc.perform(post("/api/listings")
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
     * <p>This DTO has NO optional fields: a listing without a character, a
     * bracket, a spec, a language or a schedule is not a listing. The list is
     * empty on purpose — if a field ever becomes optional, it must be added
     * here WITH its reason, and this test stops failing until it is.
     */
    @Test
    void everyFieldOfTheRequestIsDeclaredRequiredExceptTheListedOnes() {
        assertThat(requiredFieldsOf(PublishListingRequest.class))
                .containsExactlyInAnyOrder(
                        "characterId", "bracket", "spec", "languages", "schedule",
                        "schedule.zone", "schedule.windows",
                        "schedule.windows[].day", "schedule.windows[].start",
                        "schedule.windows[].end");
    }

    /**
     * The optional fields, each with the reason it may be absent. Empty by
     * design: every field of a publication request is mandatory.
     */
    private static final Map<String, String> OPTIONAL_FIELDS = Map.of();

    /**
     * The elements of a collection of scalars carry their own presence
     * constraint: {@code Set<@NotNull @NotBlank String>} rejects a null entry
     * even though the set itself is present.
     */
    @Test
    void theElementsOfASetAreConstrainedToo() {
        assertThat(elementTypeIsRequired(PublishListingRequest.class, "languages")).isTrue();
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
     * Walks the declared fields and their presence constraints, including the
     * type arguments of collections: {@code List<@NotNull @Valid WindowRequest>}
     * carries the constraints of the element type, not of the list.
     *
     * <p>The annotations live on the backing FIELDS, not on the record
     * components: a record component is a constructor parameter, and Jakarta
     * Validation targets fields. Reading the field is what the validator does.
     *
     * <p>Nested elements are reached through the ANNOTATED type, because only
     * that carries the type argument's own annotations — the plain generic
     * type erases them.
     */
    private static List<String> requiredFieldsOf(Class<?> type) {
        List<String> required = new ArrayList<>();
        for (java.lang.reflect.RecordComponent component : type.getRecordComponents()) {
            if (isRequired(type, component.getName())) {
                required.add(component.getName());
            }
            Class<?> componentType = component.getType();
            if (componentType.isRecord()) {
                // A nested record contributes its own fields, prefixed. It can
                // itself hold a collection of records, so the walk recurses.
                for (String nested : requiredFieldsOf(componentType)) {
                    required.add(component.getName() + "." + nested);
                }
            } else if (Iterable.class.isAssignableFrom(componentType)) {
                Class<?> element = elementTypeOf(component.getAnnotatedType());
                if (element != null && element.isRecord()) {
                    for (String nested : requiredFieldsOf(element)) {
                        required.add(component.getName() + "[]." + nested);
                    }
                }
            }
        }
        return required;
    }

    /**
     * The element type of a collection, read from the ANNOTATED type: the
     * presence constraints of {@code List<@NotNull @Valid WindowRequest>} live
     * on the type argument, and only the annotated type carries them.
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

    private static Stream<Arguments> requiredFields() {
        return Stream.of(
                Arguments.of("characterId",
                        body("null", BRACKET, SPEC, LANGUAGES, SCHEDULE), "characterId is required"),
                Arguments.of("bracket",
                        body(ID, "null", SPEC, LANGUAGES, SCHEDULE), "bracket is required"),
                Arguments.of("spec",
                        body(ID, BRACKET, "null", LANGUAGES, SCHEDULE), "spec is required"),
                Arguments.of("languages",
                        body(ID, BRACKET, SPEC, "null", SCHEDULE), "languages is required"),
                Arguments.of("schedule",
                        body(ID, BRACKET, SPEC, LANGUAGES, "null"), "schedule is required"),
                Arguments.of("schedule.zone",
                        body(ID, BRACKET, SPEC, LANGUAGES, schedule("null", WINDOWS)),
                        "schedule.zone is required"),
                Arguments.of("schedule.windows",
                        body(ID, BRACKET, SPEC, LANGUAGES, schedule(ZONE, "null")),
                        "schedule.windows is required"),
                Arguments.of("schedule.windows[] entries",
                        body(ID, BRACKET, SPEC, LANGUAGES, schedule(ZONE, "[null]")),
                        "schedule.windows[0] is required"),
                Arguments.of("window.day",
                        body(ID, BRACKET, SPEC, LANGUAGES, schedule(ZONE, windows("null", START, END))),
                        "schedule.windows[0].day is required"),
                Arguments.of("window.start",
                        body(ID, BRACKET, SPEC, LANGUAGES, schedule(ZONE, windows(DAY, "null", END))),
                        "schedule.windows[0].start is required"),
                Arguments.of("window.end",
                        body(ID, BRACKET, SPEC, LANGUAGES, schedule(ZONE, windows(DAY, START, "null"))),
                        "schedule.windows[0].end is required"));
    }

    private static final String ID = "\"" + CHARACTER_ID.value() + "\"";
    private static final String BRACKET = "\"2v2\"";
    private static final String SPEC = "\"MAGE_FROST\"";
    private static final String LANGUAGES = "[\"ES\"]";
    private static final String ZONE = "\"Europe/Madrid\"";
    private static final String DAY = "\"MONDAY\"";
    private static final String START = "\"18:00\"";
    private static final String END = "\"22:00\"";
    private static final String WINDOWS = windows(DAY, START, END);
    private static final String SCHEDULE = schedule(ZONE, WINDOWS);

    private static String body(String characterId, String bracket, String spec, String languages,
                               String schedule) {
        return """
                {
                  "characterId": %s,
                  "bracket": %s,
                  "spec": %s,
                  "languages": %s,
                  "schedule": %s
                }
                """.formatted(characterId, bracket, spec, languages, schedule);
    }

    private static String schedule(String zone, String windows) {
        return "{\"zone\":%s,\"windows\":%s}".formatted(zone, windows);
    }

    private static String windows(String day, String start, String end) {
        return "[{\"day\":%s,\"start\":%s,\"end\":%s}]".formatted(day, start, end);
    }

    private static String request(CharacterId characterId, String bracket, String day,
                                  String start, String end) {
        return """
                {
                  "characterId": "%s",
                  "bracket": "%s",
                  "spec": "MAGE_FROST",
                  "languages": ["ES", "EN"],
                  "schedule": {
                    "zone": "Europe/Madrid",
                    "windows": [{"day": "%s", "start": "%s", "end": "%s"}]
                  }
                }
                """.formatted(characterId.value(), bracket, day, start, end);
    }

    private static Character characterOwnedBy(PlayerId owner) {
        return Character.rehydrate(CHARACTER_ID, owner, new CharacterName("Frostbolt"),
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
        Listings listings() {
            return new FakeListings();
        }

        @Bean
        CurrentPlayerProvider currentPlayer() {
            return () -> PLAYER;
        }

        @Bean
        Clock clock() {
            return Clock.fixed(NOW, ZoneOffset.UTC);
        }

        @Bean
        PublishListing publishListing(Characters characters, Listings listings,
                                      CurrentPlayerProvider currentPlayer, Clock clock) {
            return new PublishListingService(characters, listings, currentPlayer, clock, TTL);
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

    private static final class FakeListings implements Listings {

        private final List<LfgListing> published = new ArrayList<>();
        private boolean alreadyActive;

        void reset() {
            published.clear();
            alreadyActive = false;
        }

        LfgListing only() {
            assertThat(published).hasSize(1);
            return published.getFirst();
        }

        @Override
        public void publish(LfgListing listing) {
            published.add(listing);
        }

        @Override
        public boolean hasActiveFor(CharacterId characterId, Bracket bracket, Instant now) {
            return alreadyActive;
        }
    }
}
