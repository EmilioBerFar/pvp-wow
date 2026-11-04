package com.pvpwow.lfg.application.service;

import com.pvpwow.lfg.application.port.in.RegisterPlayer;
import com.pvpwow.lfg.application.port.out.Players;
import com.pvpwow.lfg.domain.player.Player;
import com.pvpwow.lfg.domain.player.PlayerId;
import com.pvpwow.lfg.domain.shared.DomainRuleViolationException;

import java.util.Objects;

/**
 * RegisterPlayer, orchestrated. No player can own a character until it exists,
 * so this is the first door of the product.
 *
 * <p>Duplicates are a domain rule violation (422), not a conflict status
 * invented at the edge: "that handle is taken" is normal business, the caller
 * fixes the input and retries, exactly like an invalid battle tag. The unique
 * index is still the law underneath; these checks only make the common case
 * say something useful.
 *
 * <p>No {@link java.time.Clock}: a player has no temporal state yet. Adding one
 * "for later" would be a guess, and the aggregate would have to carry it.
 */
public final class RegisterPlayerService implements RegisterPlayer {

    private final Players players;

    public RegisterPlayerService(Players players) {
        this.players = Objects.requireNonNull(players, "players must not be null");
    }

    @Override
    public PlayerId register(RegisterPlayerCommand command) {
        Objects.requireNonNull(command, "command must not be null");
        if (players.usernameTaken(command.username())) {
            throw new DomainRuleViolationException(
                    "Username '%s' is already taken".formatted(command.username().value()));
        }
        if (players.battleTagTaken(command.battleTag())) {
            throw new DomainRuleViolationException(
                    "Battle tag '%s' is already registered".formatted(command.battleTag().value()));
        }
        Player player = Player.register(command.username(), command.battleTag());
        players.save(player);
        return player.id();
    }
}
