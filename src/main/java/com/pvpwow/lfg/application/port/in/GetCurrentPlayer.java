package com.pvpwow.lfg.application.port.in;

import com.pvpwow.lfg.domain.player.Player;

/**
 * Driving port: read the caller's own account.
 *
 * <p>There is no {@code GET /players/{id}} and will not be: a battle tag is
 * personal contact data, and an endpoint that resolves any id would let anyone
 * harvest them. The only address of a player is "me".
 */
public interface GetCurrentPlayer {

    Player get();
}
