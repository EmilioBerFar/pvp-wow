package com.pvpwow.lfg.application.service;

import com.pvpwow.lfg.application.port.in.SignInWithBattleNet;
import com.pvpwow.lfg.application.port.out.Players;
import com.pvpwow.lfg.domain.player.BattleNetId;
import com.pvpwow.lfg.domain.player.BattleTag;
import com.pvpwow.lfg.domain.player.Player;
import com.pvpwow.lfg.domain.player.PlayerId;

import java.util.Objects;
import java.util.Optional;

/**
 * SignInWithBattleNet, orchestrated. Two outcomes, and the order is the rule:
 *
 * <ol>
 *   <li><b>The account has been here before.</b> The battle tag it reports
 *       today is written down if it changed, and the same player comes back.
 *       Battle.net sells renames; a rename is not a new account.</li>
 *   <li><b>It has not.</b> First login: the player is born here.</li>
 * </ol>
 *
 * <p>There is no third outcome. An earlier shape could also "claim" a row
 * that had a battle tag but no account; that door is closed because a player
 * without an account cannot exist any more, and because the battle tag is
 * mutable: it is contact data, not an identity, and keying a player on it
 * would turn a rename into a second human.
 *
 * <p>The unique index on the Battle.net id is still the law. The lookup only
 * makes the common case cheap and the conflict readable.
 */
public final class SignInWithBattleNetService implements SignInWithBattleNet {

    private final Players players;

    public SignInWithBattleNetService(Players players) {
        this.players = Objects.requireNonNull(players, "players must not be null");
    }

    @Override
    public PlayerId signIn(Command command) {
        Objects.requireNonNull(command, "command must not be null");

        Optional<Player> known = players.findByBattleNetId(command.battleNetId());
        if (known.isPresent()) {
            return renameIfNeeded(known.get(), command.battleTag());
        }

        Player created = Player.register(command.battleNetId(), command.battleTag());
        players.save(created);
        return created.id();
    }

    private PlayerId renameIfNeeded(Player player, BattleTag reported) {
        if (player.battleTag().equals(reported)) {
            return player.id();
        }
        Player renamed = player.rename(reported);
        players.save(renamed);
        return renamed.id();
    }
}
