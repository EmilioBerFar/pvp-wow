package com.pvpwow.lfg.adapters.in.security;

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

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The real filter chain and the real session adapter, with the REAL database
 * and the OAuth2 client configured as it is in production.
 *
 * <p>What this cannot prove is the handshake with Battle.net itself - that is a
 * network round trip to a third party, and CI has no business making it. What
 * it does prove is everything on our side of the redirect: the board is
 * public, everything else is not, an anonymous caller gets a 401 it can parse
 * instead of a redirect it cannot, and a session carrying a player reaches the
 * use case.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(AuthenticatedCaller.PlayerIdWiring.class)
@Testcontainers
class SecurityIT {

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
        jdbc.execute("delete from players");
    }

    @Test
    void theBoardIsPublic() throws Exception {
        mockMvc.perform(get("/api/board").param("region", "EU").param("bracket", "2v2"))
                .andExpect(status().isOk());
    }

    @Test
    void anAnonymousCallerGetsA401ProblemDetailInsteadOfARedirect() throws Exception {
        mockMvc.perform(get("/api/players/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.detail").value(
                        "Not signed in: start the login at GET /oauth2/authorization/battle-net"));

        mockMvc.perform(post("/api/characters")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name": "Frostbolt", "realm": "Sanguino", "region": "EU",
                                 "wowClass": "MAGE", "specs": ["MAGE_FROST"], "itemLevel": 675}
                                """))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void aSessionThatCarriesAPlayerReachesTheUseCase() throws Exception {
        UUID playerId = UUID.randomUUID();
        jdbc.update("insert into players (id, battle_net_id, battle_tag) values (?, ?, ?)",
                playerId, "123456789", "emilio#12345");

        mockMvc.perform(AuthenticatedCaller.signedIn(get("/api/players/me"), playerId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(playerId.toString()))
                .andExpect(jsonPath("$.battleTag").value("emilio#12345"));
    }

    @Test
    void aSessionWithoutAPlayerInItIsA401NotAnEmptyIdentity() throws Exception {
        mockMvc.perform(AuthenticatedCaller.signedInWithoutAPlayer(get("/api/players/me")))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.detail").value(
                        "Not signed in: authenticate with Battle.net first"));
    }
}
