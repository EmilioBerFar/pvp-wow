package com.pvpwow.lfg.application.service;

import com.pvpwow.lfg.application.error.UnauthorizedException;
import com.pvpwow.lfg.application.port.out.Players;
import com.pvpwow.lfg.domain.player.BattleTag;
import com.pvpwow.lfg.domain.player.Player;
import com.pvpwow.lfg.domain.player.PlayerId;
import com.pvpwow.lfg.domain.player.Username;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The provider says WHOM; this service proves the player exists. An identity
 * that resolves to no row is a 401, never a 500 at the foreign key.
 */
class GetCurrentPlayerServiceTest {

    private static final PlayerId CALLER = new PlayerId(UUID.randomUUID());
    private static final Player PLAYER =
            Player.rehydrate(CALLER, new Username("emilio"), new BattleTag("emilio#12345"));

    private final FakePlayers players = new FakePlayers();
    private final GetCurrentPlayerService service =
            new GetCurrentPlayerService(players, () -> CALLER);

    @Test
    void returnsTheCallersOwnAccount() {
        players.stored = PLAYER;

        assertThat(service.get()).isEqualTo(PLAYER);
    }

    @Test
    void anIdentityWithNoPlayerIsUnauthorized() {
        assertThatThrownBy(service::get)
                .isInstanceOf(UnauthorizedException.class)
                .hasMessageContaining("Unknown player");
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
