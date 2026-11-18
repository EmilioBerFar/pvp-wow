package com.pvpwow.lfg.adapters.in.rest;

import com.pvpwow.lfg.adapters.in.security.AuthenticatedCaller;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
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
 * The player flow end to end, with the REAL filter chain and the REAL
 * database: a signed-in player owns a character, and an anonymous caller is
 * refused BEFORE any use case runs.
 *
 * <p>There is no dev profile any more. The test signs in the way the product
 * signs in - {@code oauth2Login()} for the provider handshake plus the
 * internal player id in the session - so what is exercised here is the same
 * path production takes.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(AuthenticatedCaller.PlayerIdWiring.class)
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
    void aSignedInPlayerOwnsACharacter() throws Exception {
        UUID playerId = insertPlayer();

        mockMvc.perform(AuthenticatedCaller.signedIn(get("/api/players/me"), playerId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(playerId.toString()))
                .andExpect(jsonPath("$.battleTag").value("emilio#12345"));

        mockMvc.perform(AuthenticatedCaller.signedIn(post("/api/characters"), playerId)
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
    void anAnonymousCallerIsA401BeforeAnyUseCaseRuns() throws Exception {
        mockMvc.perform(get("/api/players/me"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(post("/api/characters")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name": "Frostbolt", "realm": "Sanguino", "region": "EU",
                                 "wowClass": "MAGE", "specs": ["MAGE_FROST"], "itemLevel": 675}
                                """))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void aSessionThatNamesNoPlayerIsA401() throws Exception {
        mockMvc.perform(AuthenticatedCaller.signedInWithoutAPlayer(get("/api/players/me")))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.detail").value(containsString("Not signed in")));
    }

    private UUID insertPlayer() {
        UUID playerId = UUID.randomUUID();
        jdbc.update("insert into players (id, battle_net_id, battle_tag) values (?, ?, ?)",
                playerId, "123456789", "emilio#12345");
        return playerId;
    }
}
