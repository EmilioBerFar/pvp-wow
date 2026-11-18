package com.pvpwow.lfg.adapters.in.rest;

import com.pvpwow.lfg.application.port.in.GetCurrentPlayer;
import com.pvpwow.lfg.application.port.out.CurrentPlayerProvider;
import com.pvpwow.lfg.application.port.out.Players;
import com.pvpwow.lfg.application.service.GetCurrentPlayerService;
import com.pvpwow.lfg.domain.player.BattleNetId;
import com.pvpwow.lfg.domain.player.BattleTag;
import com.pvpwow.lfg.domain.player.Player;
import com.pvpwow.lfg.domain.player.PlayerId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Optional;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * "Who am I". There is no {@code /players/{id}} to test: the only address of
 * an account is the caller's own. The body no longer carries a handle - the
 * battle tag is the identity the product shows, and the Battle.net account id
 * stays server-side.
 */
@WebMvcTest(CurrentPlayerController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(CurrentPlayerControllerTest.FakeWiring.class)
class CurrentPlayerControllerTest {

    private static final PlayerId CALLER = new PlayerId(UUID.randomUUID());

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private Players players;

    @Autowired
    private CurrentPlayerProvider currentPlayer;

    @BeforeEach
    void resetTheStore() {
        FakePlayers store = (FakePlayers) players;
        store.stored = Player.rehydrate(CALLER, new BattleNetId("123456789"),
                new BattleTag("emilio#12345"));
        ((StubCurrentPlayer) currentPlayer).id = CALLER;
    }

    @Test
    void returnsTheCallersOwnAccount() throws Exception {
        mockMvc.perform(get("/api/players/me"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(CALLER.value().toString()))
                .andExpect(jsonPath("$.battleTag").value("emilio#12345"))
                .andExpect(jsonPath("$.username").doesNotExist());
    }

    @Test
    void anIdentityWithNoPlayerIsA401() throws Exception {
        ((StubCurrentPlayer) currentPlayer).id = new PlayerId(UUID.randomUUID());

        mockMvc.perform(get("/api/players/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.detail").value(
                        "Unknown player: the caller does not exist"));
    }

    @TestConfiguration
    static class FakeWiring {

        @Bean
        Players players() {
            return new FakePlayers();
        }

        @Bean
        CurrentPlayerProvider currentPlayer() {
            return new StubCurrentPlayer();
        }

        @Bean
        GetCurrentPlayer getCurrentPlayer(Players players, CurrentPlayerProvider currentPlayer) {
            return new GetCurrentPlayerService(players, currentPlayer);
        }
    }

    private static final class StubCurrentPlayer implements CurrentPlayerProvider {

        private PlayerId id = CALLER;

        @Override
        public PlayerId currentPlayer() {
            return id;
        }
    }

    private static final class FakePlayers implements Players {

        private Player stored;

        @Override
        public void save(Player player) {
            stored = player;
        }

        @Override
        public Optional<Player> findById(PlayerId id) {
            return Optional.ofNullable(stored).filter(player -> player.id().equals(id));
        }

        @Override
        public Optional<Player> findByBattleNetId(BattleNetId battleNetId) {
            return Optional.ofNullable(stored)
                    .filter(player -> player.battleNetId().equals(battleNetId));
        }
    }
}
