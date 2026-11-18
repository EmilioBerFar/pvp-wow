package com.pvpwow.lfg.application.error;

/**
 * The caller's identity is not a player of this board: there is no session,
 * the session names no player, or the player it names has vanished. The REST
 * edge answers 401.
 *
 * <p>WHY IT LIVES HERE AND NOT IN THE SECURITY ADAPTER: more than one layer
 * needs to say it. The session adapter says it when the session holds no
 * player; the application says it when the player it names has vanished.
 * Keeping the exception in one adapter would force the other layer to import
 * that adapter, and dependencies point inward, never sideways.
 */
public final class UnauthorizedException extends RuntimeException {

    public UnauthorizedException(String message) {
        super(message);
    }
}
