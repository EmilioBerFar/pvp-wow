package com.pvpwow.lfg.adapters.in.security;

import com.pvpwow.lfg.adapters.out.security.SessionCurrentPlayerProvider;
import org.junit.jupiter.api.AfterEach;
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

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The session is the credential, so the credential must survive a restart.
 *
 * <p>While the session lived in the heap of one instance, a deploy logged
 * everybody out and a second instance could not see the sessions of the first.
 * This test proves the opposite: the session is a ROW in PostgreSQL, and the
 * rows carry the player id that identifies the caller.
 *
 * <p>What makes this a real proof and not a mock one: the test never injects
 * a session object. It lets the request create one the way a browser would,
 * and then looks at what landed in the database. A hand-built session would be
 * stored in a map inside the test JVM, which is exactly the thing this unit
 * exists to remove.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(AuthenticatedCaller.PlayerIdWiring.class)
@Testcontainers
class JdbcSessionIT {

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
        jdbc.execute("delete from spring_session_attributes");
        jdbc.execute("delete from spring_session");
        jdbc.execute("delete from characters");
        jdbc.execute("delete from join_requests");
        jdbc.execute("delete from players");
    }

    @Test
    void aSignedInSessionIsStoredInTheDatabaseNotInTheHeap() throws Exception {
        UUID playerId = UUID.randomUUID();
        jdbc.update("insert into players (id, battle_net_id, battle_tag) values (?, ?, ?)",
                playerId, "123456789", "emilio#12345");

        mockMvc.perform(AuthenticatedCaller.signedIn(get("/api/players/me"), playerId))
                .andExpect(status().isOk());

        // The session is a ROW now, not an object in a map that a restart
        // would empty.
        assertThat(countIn("spring_session")).isPositive();
        assertThat(countIn("spring_session_attributes")).isPositive();
    }

    @Test
    void theStoredSessionCarriesThePlayerIdThatIdentifiesTheCaller() throws Exception {
        UUID playerId = UUID.randomUUID();
        jdbc.update("insert into players (id, battle_net_id, battle_tag) values (?, ?, ?)",
                playerId, "123456789", "emilio#12345");

        mockMvc.perform(AuthenticatedCaller.signedIn(get("/api/players/me"), playerId))
                .andExpect(status().isOk());

        // The attribute is stored as serialized bytes, so the proof is that
        // deserializing it gives back THE player id, not that some bytes
        // exist.
        assertThat(storedPlayerId()).isEqualTo(playerId);
    }

    @Test
    void theAttributesOfASessionDieWithTheSession() throws Exception {
        UUID playerId = UUID.randomUUID();
        jdbc.update("insert into players (id, battle_net_id, battle_tag) values (?, ?, ?)",
                playerId, "123456789", "emilio#12345");

        mockMvc.perform(AuthenticatedCaller.signedIn(get("/api/players/me"), playerId))
                .andExpect(status().isOk());
        assertThat(countIn("spring_session_attributes")).isPositive();

        jdbc.execute("delete from spring_session");

        // The foreign key cascades: an attribute without its session is a row
        // nobody will ever read.
        assertThat(countIn("spring_session_attributes")).isZero();
    }

    /**
     * Reads back the player id the session stored, the way the next instance
     * would read it: deserialize the bytes.
     */
    private UUID storedPlayerId() {
        byte[] bytes = jdbc.queryForObject(
                "select attribute_bytes from spring_session_attributes "
                        + "where attribute_name = ?",
                byte[].class,
                SessionCurrentPlayerProvider.PLAYER_ID_ATTRIBUTE);
        assertThat(bytes).isNotNull();
        try (ObjectInputStream in = new ObjectInputStream(new ByteArrayInputStream(bytes))) {
            return (UUID) in.readObject();
        } catch (IOException | ClassNotFoundException notADeserializableAttribute) {
            throw new AssertionError(
                    "the stored player id could not be read back", notADeserializableAttribute);
        }
    }

    private int countIn(String table) {
        return jdbc.queryForObject("select count(*) from " + table, Integer.class);
    }
}
