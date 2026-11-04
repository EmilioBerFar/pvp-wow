package com.pvpwow.lfg.application.port.in;

import com.pvpwow.lfg.domain.player.BattleTag;
import com.pvpwow.lfg.domain.player.PlayerId;
import com.pvpwow.lfg.domain.player.Username;

import java.util.Objects;

/**
 * Driving port: register a player. The account that owns characters must exist
 * before any character points at it.
 *
 * <p>Unauthenticated on purpose: this IS the sign-up. Battle.net OAuth (the
 * next work unit) will replace the dev header, not this use case — logging in
 * creates or recovers the same aggregate.
 */
public interface RegisterPlayer {

    PlayerId register(RegisterPlayerCommand command);

    record RegisterPlayerCommand(Username username, BattleTag battleTag) {

        public RegisterPlayerCommand {
            Objects.requireNonNull(username, "username must not be null");
            Objects.requireNonNull(battleTag, "battleTag must not be null");
        }
    }
}
