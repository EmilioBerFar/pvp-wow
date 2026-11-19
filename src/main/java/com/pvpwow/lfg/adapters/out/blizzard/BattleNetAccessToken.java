package com.pvpwow.lfg.adapters.out.blizzard;

/**
 * The Battle.net access token, for the calls that need it.
 *
 * <p>An interface because the client that speaks to Blizzard must be
 * testable without a session, a token and a database. The test asks for a
 * token it holds; the production implementation asks the session.
 */
interface BattleNetAccessToken {

    /**
     * The access token of the signed-in player.
     *
     * @throws com.pvpwow.lfg.application.error.UnauthorizedException when
     *         there is no token - the player has not signed in, or the
     *         session was invalidated - or when the token has expired. Both
     *         are the same thing to the caller: the caller must sign in
     *         again.
     */
    String accessTokenOfCurrentPlayer();
}
