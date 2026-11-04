package com.pvpwow.lfg.adapters.in.rest;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

import java.util.UUID;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The WU17 rule end to end, with the REAL header adapter and the REAL
 * database: a player signs up, the returned id becomes an identity, and a
 * header that names no player is refused BEFORE any use case runs.
 *
 * <p>A web slice with a fake provider cannot prove this, because the rule lives
 * in the adapter it replaces.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class PlayerFlowIT {

    @Container
    static PostgreSQLContainer postgres =
            new PostgreSQLContainer(DockerImageName.parse("postgres:18-alpine"));

    @DynamicPropertySource
    static void pointTheApplicationAtTheContainer(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbc;

    @AfterEach
    void emptyTheDatabase() {
        jdbc.execute("delete from characters");
        jdbc.execute("delete from join_requests");
        jdbc.execute("delete from listing_outcomes");
        jdbc.execute("delete from lfg_listings");
        jdbc.execute("delete from players");
    }

    @Test
    void aPlayerSignsUpAndThenOwnsACharacter() throws Exception {
        String playerId = signUp("Emilio", "Emilio#12345");

        mockMvc.perform(get("/api/players/me").header("X-Player-Id", playerId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(playerId))
                .andExpect(jsonPath("$.username").value("Emilio"))
                .andExpect(jsonPath("$.battleTag").value("emilio#12345"));

        mockMvc.perform(post("/api/characters")
                        .header("X-Player-Id", playerId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name": "Frostbolt", "realm": "Sanguino", "region": "EU",
                                 "wowClass": "MAGE", "specs": ["MAGE_FROST"],
                                 "itemLevel": 675, "ratings": {"3v3": 2100}}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNotEmpty());
    }

    @Test
    void aHeaderThatNamesNoPlayerIsA401BeforeAnyUseCaseRuns() throws Exception {
        mockMvc.perform(get("/api/players/me")
                        .header("X-Player-Id", UUID.randomUUID().toString()))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.detail").value(containsString("Unknown player")));

        mockMvc.perform(post("/api/characters")
                        .header("X-Player-Id", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name": "Frostbolt", "realm": "Sanguino", "region": "EU",
                                 "wowClass": "MAGE", "specs": ["MAGE_FROST"], "itemLevel": 675}
                                """))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(get("/api/players/me"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void twoPlayersCannotShareAHandleOrABattleTag() throws Exception {
        signUp("Emilio", "Emilio#12345");

        mockMvc.perform(post("/api/players")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username": "emilio", "battleTag": "other#12345"}
                                """))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.detail").value(
                        "Username 'emilio' is already taken"));

        mockMvc.perform(post("/api/players")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username": "someone", "battleTag": "EMILIO#12345"}
                                """))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.detail").value(
                        "Battle tag 'emilio#12345' is already registered"));
    }

    private String signUp(String username, String battleTag) throws Exception {
        String body = mockMvc.perform(post("/api/players")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username": "%s", "battleTag": "%s"}
                                """.formatted(username, battleTag)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.id");
    }
}
