package com.pvpwow.lfg.application.error;

/**
 * The caller's identity is not a player of this board: the header is absent,
 * malformed, or names an id that no player holds. The REST edge answers 401.
 *
 * <p>WHY IT LIVES HERE AND NOT IN THE SECURITY ADAPTER: more than one adapter
 * needs to say it. The dev header adapter says it when the header is missing;
 * the application says it when the player it names has vanished. Keeping the
 * exception in one adapter would force the other layer to import that adapter,
 * and dependencies point inward, never sideways.
 */
public final class UnauthorizedException extends RuntimeException {

    public UnauthorizedException(String message) {
        super(message);
    }
}
