package com.pvpwow.lfg.application.port.in;

import com.pvpwow.lfg.domain.player.BattleNetId;
import com.pvpwow.lfg.domain.player.BattleTag;
import com.pvpwow.lfg.domain.player.PlayerId;

import java.util.Objects;

/**
 * Driving port: turn a Battle.net login into a player.
 *
 * <p>Idempotent on purpose. Signing in is not a registration with a hidden
 * duplicate check: the same account may log in a hundred times, and every one
 * of them must land on the same player, or that player's characters, listings
 * and history would be split across rows nobody can merge.
 *
 * <p>What replaced the old {@code RegisterPlayer}: there is no longer a
 * sign-up form, so there is no longer a use case that takes a handle nobody
 * verified.
 */
public interface SignInWithBattleNet {

    PlayerId signIn(Command command);

    record Command(BattleNetId battleNetId, BattleTag battleTag) {

        public Command {
            Objects.requireNonNull(battleNetId, "battleNetId must not be null");
            Objects.requireNonNull(battleTag, "battleTag must not be null");
        }
    }
}
