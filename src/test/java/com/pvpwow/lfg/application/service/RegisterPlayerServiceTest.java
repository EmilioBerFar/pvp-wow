package com.pvpwow.lfg.application.service;

import com.pvpwow.lfg.application.port.in.RegisterPlayer.RegisterPlayerCommand;
import com.pvpwow.lfg.application.port.out.Players;
import com.pvpwow.lfg.domain.player.BattleTag;
import com.pvpwow.lfg.domain.player.Player;
import com.pvpwow.lfg.domain.player.PlayerId;
import com.pvpwow.lfg.domain.player.Username;
import com.pvpwow.lfg.domain.shared.DomainRuleViolationException;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The first door of the product: without a player there is nobody to own a
 * character. Duplicates are a 422 (normal business the caller can fix), and
 * nothing is saved when a check fails.
 */
class RegisterPlayerServiceTest {

    private final FakePlayers players = new FakePlayers();
    private final RegisterPlayerService service = new RegisterPlayerService(players);

    @Test
    void registersThePlayerAndReturnsItsIdentity() {
        PlayerId id = service.register(
                new RegisterPlayerCommand(new Username("emilio"), new BattleTag("Emilio#12345")));

        assertThat(players.stored).singleElement().satisfies(player -> {
            assertThat(player.id()).isEqualTo(id);
            assertThat(player.username()).isEqualTo(new Username("emilio"));
            assertThat(player.battleTag())
                    .as("the value object folds the tag, so the stored one is lowercase")
                    .isEqualTo(new BattleTag("emilio#12345"));
        });
    }

    @Test
    void aUsernameTakenInAnotherCaseIsRejectedAndNothingIsSaved() {
        players.store(Player.register(new Username("emilio"), new BattleTag("other#1234")));

        assertThatThrownBy(() -> service.register(
                new RegisterPlayerCommand(new Username("Emilio"), new BattleTag("emilio#12345"))))
                .isInstanceOf(DomainRuleViolationException.class)
                .hasMessage("Username 'Emilio' is already taken");
        assertThat(players.stored).hasSize(1);
    }

    @Test
    void aBattleTagAlreadyRegisteredIsRejected() {
        players.store(Player.register(new Username("someone"), new BattleTag("emilio#12345")));

        assertThatThrownBy(() -> service.register(
                new RegisterPlayerCommand(new Username("emilio"), new BattleTag("Emilio#12345"))))
                .isInstanceOf(DomainRuleViolationException.class)
                .hasMessage("Battle tag 'emilio#12345' is already registered");
    }

    /**
     * The uniqueness pre-check needs a populated store, but a rejected
     * registration must not write: a 422 that still inserted a row would make
     * the retry fail for the wrong reason.
     */
    @Test
    void aRejectedRegistrationSavesNothing() {
        players.store(Player.register(new Username("emilio"), new BattleTag("other#1234")));
        int before = players.stored.size();

        assertThatThrownBy(() -> service.register(
                new RegisterPlayerCommand(new Username("emilio"), new BattleTag("emilio#12345"))))
                .isInstanceOf(DomainRuleViolationException.class);
        assertThat(players.stored).hasSize(before);
    }

    private static final class FakePlayers implements Players {

        private final List<Player> stored = new ArrayList<>();

        void store(Player player) {
            stored.add(player);
        }

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
