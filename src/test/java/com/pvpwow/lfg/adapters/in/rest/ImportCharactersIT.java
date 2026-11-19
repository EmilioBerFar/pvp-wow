package com.pvpwow.lfg.adapters.in.rest;

import com.pvpwow.lfg.adapters.in.security.AuthenticatedCaller;
import com.pvpwow.lfg.adapters.out.blizzard.StubBlizzard;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The import, end to end: the listing, the choice, and the row in the
 * database.
 *
 * <p>It talks to a STUB Blizzard, never to the real one: a test run has no
 * business calling a third party, and a test that did would depend on
 * somebody's account. The stub keeps the upstream's rules - the paths, the
 * summary that lists only the brackets a character HAS rated, and the 404 for
 * a bracket nobody played.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(AuthenticatedCaller.PlayerIdWiring.class)
@Testcontainers
class ImportCharactersIT {

    private static final long BLIZZARD_ID = 161996209L;

    @Container
    static PostgreSQLContainer postgres =
            new PostgreSQLContainer(DockerImageName.parse("postgres:18-alpine"));

    private static StubBlizzard blizzard;

    @BeforeAll
    static void startTheStubProvider() throws Exception {
        blizzard = new StubBlizzard();
    }

    @AfterAll
    static void stopTheStubProvider() {
        if (blizzard != null) {
            blizzard.close();
        }
    }

    @DynamicPropertySource
    static void pointTheApplicationAtTheStubs(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        // The stub, not Blizzard. The property exists so that a test run
        // never calls a third party.
        registry.add("pvp.blizzard.base-url", () -> blizzard.baseUrl());
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private org.springframework.security.oauth2.client.OAuth2AuthorizedClientService
            authorizedClients;

    @Autowired
    private org.springframework.security.oauth2.client.registration.ClientRegistrationRepository
            registrations;

    @AfterEach
    void emptyTheDatabase() {
        jdbc.execute("delete from characters");
        jdbc.execute("delete from join_requests");
        jdbc.execute("delete from listing_outcomes");
        jdbc.execute("delete from lfg_listings");
        jdbc.execute("delete from players");
        jdbc.execute("delete from oauth2_authorized_client");
    }

    @Test
    void theListingShowsTheCharactersOfTheAccount() throws Exception {
        UUID playerId = insertPlayer();
        blizzard.withAccount(BLIZZARD_ID, "Strongem", "tarren-mill", "Mage", 70);

        mockMvc.perform(AuthenticatedCaller.signedIn(
                        get("/api/characters/importable?region=EU"), playerId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].blizzardId").value(BLIZZARD_ID))
                .andExpect(jsonPath("$[0].name").value("Strongem"))
                .andExpect(jsonPath("$[0].realmSlug").value("tarren-mill"))
                .andExpect(jsonPath("$[0].className").value("Mage"))
                .andExpect(jsonPath("$[0].level").value(70));
    }

    @Test
    void theListingWritesNothing() throws Exception {
        UUID playerId = insertPlayer();
        blizzard.withAccount(BLIZZARD_ID, "Strongem", "tarren-mill", "Mage", 70);

        mockMvc.perform(AuthenticatedCaller.signedIn(
                get("/api/characters/importable?region=EU"), playerId));

        assertThatCharacterCount(0);
    }

    @Test
    void theChosenCharacterIsImportedWithTheFactsBlizzardReports() throws Exception {
        UUID playerId = insertPlayer();
        blizzard.withAccount(BLIZZARD_ID, "Strongem", "tarren-mill", "Mage", 70);
        blizzard.withCharacter("tarren-mill", "Strongem", "Mage", "Frost", 702)
                .withSummary("tarren-mill", "Strongem", "2v2")
                .withBracket("tarren-mill", "Strongem", "2v2", 1800, 43);

        mockMvc.perform(AuthenticatedCaller.signedIn(
                        post("/api/characters/import?region=EU"), playerId)
                        .contentType("application/json")
                        .content("""
                                {"characters": [
                                  {"blizzardId": %d}
                                ]}
                                """.formatted(BLIZZARD_ID)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.imported").value(1))
                .andExpect(jsonPath("$.outcomes[0].outcome").value("IMPORTED"));

        // The row is there, with the facts the upstream reported.
        assertThat(jdbc.queryForObject(
                "select count(*) from characters", Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject(
                "select blizzard_character_id from characters", Long.class)).isEqualTo(BLIZZARD_ID);
        assertThat(jdbc.queryForObject(
                "select item_level from characters", Integer.class)).isEqualTo(702);
        assertThat(jdbc.queryForObject(
                "select rating from character_standings where bracket = 'TWO_VS_TWO'",
                Integer.class)).isEqualTo(1800);
        assertThat(jdbc.queryForObject(
                "select season_id from character_standings where bracket = 'TWO_VS_TWO'",
                Integer.class)).isEqualTo(43);
    }

    @Test
    void aCharacterThatIsAlreadyOursIsRefreshedAndNotDuplicated() throws Exception {
        UUID playerId = insertPlayer();
        blizzard.withAccount(BLIZZARD_ID, "Strongem", "tarren-mill", "Mage", 70);
        blizzard.withCharacter("tarren-mill", "Strongem", "Mage", "Frost", 702)
                .withSummary("tarren-mill", "Strongem");
        importIt(playerId, BLIZZARD_ID);

        // The gear changed since the last import.
        blizzard.withCharacter("tarren-mill", "Strongem", "Mage", "Frost", 750)
                .withSummary("tarren-mill", "Strongem");

        mockMvc.perform(AuthenticatedCaller.signedIn(
                        post("/api/characters/import?region=EU"), playerId)
                        .contentType("application/json")
                        .content("""
                                {"characters": [
                                  {"blizzardId": %d}
                                ]}
                                """.formatted(BLIZZARD_ID)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.refreshed").value(1))
                .andExpect(jsonPath("$.imported").value(0));

        // ONE row, with the new gear.
        assertThatCharacterCount(1);
        assertThat(jdbc.queryForObject(
                "select item_level from characters", Integer.class)).isEqualTo(750);
    }

    @Test
    void aCharacterWeDoNotKnowIsRejectedAndTheOthersAreImported() throws Exception {
        UUID playerId = insertPlayer();
        blizzard.withAccount(BLIZZARD_ID, "GoodOne", "tarren-mill", "Mage", 70);
        blizzard.withCharacter("tarren-mill", "GoodOne", "Mage", "Frost", 702)
                .withSummary("tarren-mill", "GoodOne");
        // The second character is NOT in the account: the import cannot ask
        // Blizzard about it, and it is refused.
        blizzard.withCharacter("tarren-mill", "LittleOne", "Mage", null, 120)
                .withSummary("tarren-mill", "LittleOne");

        mockMvc.perform(AuthenticatedCaller.signedIn(
                        post("/api/characters/import?region=EU"), playerId)
                        .contentType("application/json")
                        .content("""
                                {"characters": [
                                  {"blizzardId": %d},
                                  {"blizzardId": 999}
                                ]}
                                """.formatted(BLIZZARD_ID)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.imported").value(1))
                .andExpect(jsonPath("$.rejected").value(1));

        // The good one is in; the refused one is not, and the batch carried on.
        assertThatCharacterCount(1);
    }

    @Test
    void aCharacterThatIsNotInTheAccountIsRejected() throws Exception {
        // The character endpoint of the Blizzard API is PUBLIC: it answers
        // for any character in the game. So the client sends an id and the
        // server checks it against the account of the signed-in player.
        UUID playerId = insertPlayer();
        blizzard.withAccount(BLIZZARD_ID, "Strongem", "tarren-mill", "Mage", 70);
        // A character that IS in the account.
        blizzard.withAccount(BLIZZARD_ID, "Strongem", "tarren-mill", "Mage", 70);
        blizzard.withCharacter("tarren-mill", "Strongem", "Mage", "Frost", 702)
                .withSummary("tarren-mill", "Strongem");

        mockMvc.perform(AuthenticatedCaller.signedIn(
                        post("/api/characters/import?region=EU"), playerId)
                        .contentType("application/json")
                        .content("""
                                {"characters": [{"blizzardId": 424242}]}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.imported").value(0))
                .andExpect(jsonPath("$.rejected").value(1));

        assertThatCharacterCount(0);
    }

    @Test
    void anUnknownRegionIsA422() throws Exception {
        UUID playerId = insertPlayer();

        mockMvc.perform(AuthenticatedCaller.signedIn(
                        get("/api/characters/importable?region=Atlantis"), playerId))
                .andExpect(status().isUnprocessableEntity());
    }

    private void importIt(UUID playerId, long blizzardId) throws Exception {
        mockMvc.perform(AuthenticatedCaller.signedIn(
                        post("/api/characters/import?region=EU"), playerId)
                        .contentType("application/json")
                        .content("""
                                {"characters": [
                                  {"blizzardId": %d}
                                ]}
                                """.formatted(blizzardId)))
                .andExpect(status().isOk());
    }

    private void assertThatCharacterCount(int expected) {
        assertThat(jdbc.queryForObject("select count(*) from characters", Integer.class))
                .isEqualTo(expected);
    }

    private UUID insertPlayer() {
        UUID playerId = UUID.randomUUID();
        jdbc.update("insert into players (id, battle_net_id, battle_tag) values (?, ?, ?)",
                playerId, "123456789", "emilio#12345");
        // The authorized client, the way the login leaves it: the Battle.net
        // account id is the key, and it holds the token the import calls
        // Blizzard with.
        authorizedClients.saveAuthorizedClient(
                new org.springframework.security.oauth2.client.OAuth2AuthorizedClient(
                        registrations.findByRegistrationId("battle-net"),
                        "123456789",
                        new org.springframework.security.oauth2.core.OAuth2AccessToken(
                                org.springframework.security.oauth2.core.OAuth2AccessToken.TokenType.BEARER,
                                "the-access-token",
                                java.time.Instant.now(),
                                java.time.Instant.now().plusSeconds(3600),
                                java.util.Set.of("wow.profile"))),
                new org.springframework.security.authentication.TestingAuthenticationToken(
                        "123456789", "no-credentials-here"));
        return playerId;
    }
}
