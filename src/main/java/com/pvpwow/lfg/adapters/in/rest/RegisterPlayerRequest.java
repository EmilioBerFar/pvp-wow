package com.pvpwow.lfg.adapters.in.rest;

import com.pvpwow.lfg.application.port.in.RegisterPlayer.RegisterPlayerCommand;
import com.pvpwow.lfg.domain.player.BattleTag;
import com.pvpwow.lfg.domain.player.Username;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * Wire representation of a sign-up. Presence is Bean Validation, so a field
 * added without {@code @NotNull} fails the suite instead of production; the
 * FORMAT of each value stays where it belongs, in the value object, and an
 * invalid one dies there as a 422.
 */
record RegisterPlayerRequest(
        @NotNull @NotBlank String username,
        @NotNull @NotBlank String battleTag) {

    RegisterPlayerCommand toCommand() {
        return new RegisterPlayerCommand(
                new Username(WireParsing.required(username, "username")),
                new BattleTag(WireParsing.required(battleTag, "battleTag")));
    }
}
