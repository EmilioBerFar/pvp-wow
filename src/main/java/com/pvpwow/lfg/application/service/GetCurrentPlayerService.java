package com.pvpwow.lfg.application.service;

import com.pvpwow.lfg.application.error.UnauthorizedException;
import com.pvpwow.lfg.application.port.in.GetCurrentPlayer;
import com.pvpwow.lfg.application.port.out.CurrentPlayerProvider;
import com.pvpwow.lfg.application.port.out.Players;
import com.pvpwow.lfg.domain.player.Player;

import java.util.Objects;

/**
 * GetCurrentPlayer, orchestrated. Two reads on purpose: the provider says WHO
 * is calling, this service proves that player still exists.
 *
 * <p>Defense in depth, not duplication. The dev header adapter already checks
 * existence, but the port contract does not promise it: a future OAuth adapter
 * could resolve a Battle.net id to a player row in another step. The
 * application must never hand a nonexistent owner to a use case — the foreign
 * key would answer with a 500 instead of a 401.
 */
public final class GetCurrentPlayerService implements GetCurrentPlayer {

    private final Players players;
    private final CurrentPlayerProvider currentPlayer;

    public GetCurrentPlayerService(Players players, CurrentPlayerProvider currentPlayer) {
        this.players = Objects.requireNonNull(players, "players must not be null");
        this.currentPlayer = Objects.requireNonNull(currentPlayer,
                "currentPlayer must not be null");
    }

    @Override
    public Player get() {
        return players.findById(currentPlayer.currentPlayer())
                .orElseThrow(() -> new UnauthorizedException(
                        "Unknown player: the caller does not exist"));
    }
}
