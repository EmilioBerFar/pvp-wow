package com.pvpwow.lfg.application.service;

import com.pvpwow.lfg.application.port.in.SignInWithBattleNet.Command;
import com.pvpwow.lfg.application.port.out.Players;
import com.pvpwow.lfg.domain.player.BattleNetId;
import com.pvpwow.lfg.domain.player.BattleTag;
import com.pvpwow.lfg.domain.player.Player;
import com.pvpwow.lfg.domain.player.PlayerId;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The two outcomes of a login: the account comes back, or the player is born.
 */
class SignInWithBattleNetServiceTest {

    private static final BattleNetId ACCOUNT = new BattleNetId("123456789");
    private static final BattleTag TAG = new BattleTag("emilio#12345");

    private final InMemoryPlayers players = new InMemoryPlayers();
    private final SignInWithBattleNetService service = new SignInWithBattleNetService(players);

    @Test
    void theFirstLoginCreatesAPlayerLinkedToTheAccount() {
        PlayerId id = service.signIn(new Command(ACCOUNT, TAG));

        Player stored = players.findById(id).orElseThrow();
        assertThat(stored.battleNetId()).isEqualTo(ACCOUNT);
        assertThat(stored.battleTag()).isEqualTo(TAG);
    }

    @Test
    void theSecondLoginReturnsTheSamePlayerAndWritesNothing() {
        PlayerId first = service.signIn(new Command(ACCOUNT, TAG));
        players.writes.clear();

        PlayerId second = service.signIn(new Command(ACCOUNT, TAG));

        assertThat(second).isEqualTo(first);
        assertThat(players.writes).isEmpty();
    }

    @Test
    void aRenamedBattleTagIsRecordedOnTheSamePlayer() {
        PlayerId id = service.signIn(new Command(ACCOUNT, TAG));
        BattleTag renamed = new BattleTag("newemilio#99999");

        PlayerId after = service.signIn(new Command(ACCOUNT, renamed));

        assertThat(after).isEqualTo(id);
        assertThat(players.findById(id).orElseThrow().battleTag()).isEqualTo(renamed);
    }

    @Test
    void twoAccountsMayCarryTheSameBattleTagBecauseBlizzardReclaimsThem() {
        PlayerId first = service.signIn(new Command(ACCOUNT, TAG));
        PlayerId second = service.signIn(new Command(new BattleNetId("2"), TAG));

        assertThat(second).isNotEqualTo(first);
        assertThat(players.findById(second).orElseThrow().battleTag()).isEqualTo(TAG);
    }

    @Test
    void aLoginWithoutAnAccountOrATagIsRejectedBeforeAnyLookup() {
        assertThatThrownBy(() -> new Command(null, TAG))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new Command(ACCOUNT, null))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> service.signIn(null))
                .isInstanceOf(NullPointerException.class);
    }

    private static final class InMemoryPlayers implements Players {

        private final Map<UUID, Player> rows = new LinkedHashMap<>();
        private final List<Player> writes = new ArrayList<>();

        @Override
        public void save(Player player) {
            rows.put(player.id().value(), player);
            writes.add(player);
        }

        @Override
        public Optional<Player> findById(PlayerId id) {
            return Optional.ofNullable(rows.get(id.value()));
        }

        @Override
        public Optional<Player> findByBattleNetId(BattleNetId battleNetId) {
            return rows.values().stream()
                    .filter(player -> player.battleNetId().equals(battleNetId))
                    .findFirst();
        }
    }
}
