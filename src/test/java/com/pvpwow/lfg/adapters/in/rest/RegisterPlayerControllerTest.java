package com.pvpwow.lfg.adapters.in.rest;

import com.jayway.jsonpath.JsonPath;
import com.pvpwow.lfg.application.port.in.RegisterPlayer;
import com.pvpwow.lfg.application.port.out.Players;
import com.pvpwow.lfg.application.service.RegisterPlayerService;
import com.pvpwow.lfg.domain.player.BattleTag;
import com.pvpwow.lfg.domain.player.Player;
import com.pvpwow.lfg.domain.player.PlayerId;
import com.pvpwow.lfg.domain.player.Username;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The sign-up contract, at the wire. It is deliberately the only unauthenticated
 * write in the API: there is no identity to present before the identity exists.
 */
@WebMvcTest(RegisterPlayerController.class)
@Import(RegisterPlayerControllerTest.FakeWiring.class)
class RegisterPlayerControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private Players players;

    @BeforeEach
    void resetTheStore() {
        fake().stored.clear();
    }

    @Test
    void createsTheAccountAndReturnsItsId() throws Exception {
        String body = mockMvc.perform(post("/api/players")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username": "Emilio", "battleTag": "Emilio#12345"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNotEmpty())
                .andExpect(header().doesNotExist("Location"))
                .andReturn().getResponse().getContentAsString();

        String id = JsonPath.read(body, "$.id");
        assertThat(fake().stored)
                .singleElement()
                .satisfies(player -> {
                    assertThat(player.id().value().toString()).isEqualTo(id);
                    assertThat(player.username()).isEqualTo(new Username("Emilio"));
                    assertThat(player.battleTag())
                            .isEqualTo(new BattleTag("emilio#12345"));
                });
    }

    @Test
    void aMissingUsernameIsA422ThatNamesTheField() throws Exception {
        mockMvc.perform(post("/api/players")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"battleTag": "emilio#12345"}
                                """))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.detail").value("username is required"));
    }

    @Test
    void aMalformedBattleTagIsA422() throws Exception {
        mockMvc.perform(post("/api/players")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username": "emilio", "battleTag": "emilio"}
                                """))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.detail").value(
                        containsString("Battle tag must look like")));
    }

    @Test
    void aTakenUsernameIsA422AndNothingIsCreated() throws Exception {
        fake().stored.add(Player.register(new Username("emilio"),
                new BattleTag("someone#1234")));

        mockMvc.perform(post("/api/players")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username": "Emilio", "battleTag": "emilio#12345"}
                                """))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.detail").value("Username 'Emilio' is already taken"));

        assertThat(fake().stored).hasSize(1);
    }

    @Test
    void aSyntacticallyBrokenBodyIsA400() throws Exception {
        mockMvc.perform(post("/api/players")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\": "))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Malformed request body"));
    }

    private FakePlayers fake() {
        return (FakePlayers) players;
    }

    @TestConfiguration
    static class FakeWiring {

        @Bean
        Players players() {
            return new FakePlayers();
        }

        @Bean
        RegisterPlayer registerPlayer(Players players) {
            return new RegisterPlayerService(players);
        }
    }

    private static final class FakePlayers implements Players {

        private final List<Player> stored = new ArrayList<>();

        @Override
        public void save(Player player) {
            stored.add(player);
        }

        @Override
        public Optional<Player> findById(PlayerId id) {
            return stored.stream().filter(player -> player.id().equals(id)).findFirst();
        }

        @Override
        public boolean usernameTaken(Username username) {
            return stored.stream().anyMatch(player ->
                    player.username().value().equalsIgnoreCase(username.value()));
        }

        @Override
        public boolean battleTagTaken(BattleTag battleTag) {
            return stored.stream().anyMatch(player -> player.battleTag().equals(battleTag));
        }
    }
}
