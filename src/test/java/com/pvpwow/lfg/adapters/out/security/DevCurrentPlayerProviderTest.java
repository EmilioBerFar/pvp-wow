package com.pvpwow.lfg.adapters.out.security;

import com.pvpwow.lfg.application.error.UnauthorizedException;
import com.pvpwow.lfg.application.port.out.Players;
import com.pvpwow.lfg.domain.player.BattleTag;
import com.pvpwow.lfg.domain.player.Player;
import com.pvpwow.lfg.domain.player.PlayerId;
import com.pvpwow.lfg.domain.player.Username;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The dev header is a CLAIM. These tests pin the difference between "says who
 * they are" and "is who they say": an id that names no player is not an
 * identity, even when it is a perfectly valid UUID.
 */
class DevCurrentPlayerProviderTest {

    private static final PlayerId KNOWN = new PlayerId(UUID.randomUUID());

    private final FakePlayers players = new FakePlayers();
    private final DevCurrentPlayerProvider provider = new DevCurrentPlayerProvider(players);

    @BeforeEach
    void theKnownPlayerExists() {
        players.stored = Player.rehydrate(KNOWN,
                new Username("emilio"), new BattleTag("emilio#12345"));
    }

    @AfterEach
    void cleanUpRequestContext() {
        RequestContextHolder.resetRequestAttributes();
    }

    @Test
    void resolvesThePlayerFromTheHeader() {
        requestWithHeader("X-Player-Id", KNOWN.value().toString());

        assertThat(provider.currentPlayer()).isEqualTo(KNOWN);
    }

    @Test
    void aMissingHeaderIsUnauthorized() {
        RequestContextHolder.setRequestAttributes(
                new ServletRequestAttributes(new MockHttpServletRequest()));

        assertThatThrownBy(provider::currentPlayer)
                .isInstanceOf(UnauthorizedException.class)
                .hasMessageContaining("X-Player-Id");
    }

    @Test
    void aGarbageUuidIsUnauthorized() {
        requestWithHeader("X-Player-Id", "not-a-uuid");

        assertThatThrownBy(provider::currentPlayer)
                .isInstanceOf(UnauthorizedException.class)
                .hasMessageContaining("UUID");
    }

    @Test
    void aWellFormedUuidThatNamesNoPlayerIsUnauthorized() {
        requestWithHeader("X-Player-Id", UUID.randomUUID().toString());

        assertThatThrownBy(provider::currentPlayer)
                .isInstanceOf(UnauthorizedException.class)
                .hasMessageContaining("Unknown player");
    }

    @Test
    void noRequestContextAtAllIsUnauthorized() {
        assertThatThrownBy(provider::currentPlayer)
                .isInstanceOf(UnauthorizedException.class)
                .hasMessageContaining("request context");
    }

    private void requestWithHeader(String name, String value) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(name, value);
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
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
        public boolean usernameTaken(Username username) {
            return false;
        }

        @Override
        public boolean battleTagTaken(BattleTag battleTag) {
            return false;
        }
    }
}
