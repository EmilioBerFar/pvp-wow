package com.pvpwow.lfg.adapters.out.blizzard;

import com.pvpwow.lfg.application.error.UnauthorizedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientService;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.OAuth2AccessToken;
import org.springframework.stereotype.Component;

import java.time.Instant;

/**
 * The token of the signed-in player, read from the authorized client the
 * login saved.
 *
 * <p>WHERE THE TOKEN COMES FROM: the authorized client that the login filter
 * saved, keyed by the Battle.net account id ({@code sub}). That is the same
 * row the login wrote, so the token survives a restart - which matters
 * because the import happens days after the login.
 *
 * <p>WHY THE ACCOUNT ID IS NOT READ FROM THE SESSION: the session carries
 * OUR player id, and turning that into a Battle.net account id would be a
 * database read on every call to Blizzard. It does not have to be: the
 * current authentication IS the Battle.net account. {@code
 * OAuth2AuthenticationToken.getName()} returns the {@code sub} - the very
 * value the authorized client was filed under by the login filter - so the
 * lookup key is already in hand, with no query and without the session
 * carrying a second copy of it.
 *
 * <p>WHY AN EXPIRED TOKEN IS AN ERROR AND NOT A REFRESH: Battle.net issues
 * refresh tokens, and this port deliberately does not use them. A refresh
 * token is the most dangerous credential in the whole project, and the
 * product does not need it: the import happens while the player is signed
 * in, and a token that expired means the player has to sign in again. That
 * is a smaller problem than a stored, encrypted, rotating refresh token.
 */
@Component
final class SessionBattleNetAccessToken implements BattleNetAccessToken {

    /**
     * The registration id, the same one the URLs are built from:
     * {@code /oauth2/authorization/battle-net}.
     */
    private static final String REGISTRATION_ID = "battle-net";

    private final OAuth2AuthorizedClientService authorizedClients;

    SessionBattleNetAccessToken(OAuth2AuthorizedClientService authorizedClients) {
        this.authorizedClients = authorizedClients;
    }

    @Override
    public String accessTokenOfCurrentPlayer() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (!(authentication instanceof OAuth2AuthenticationToken battleNet)) {
            throw new UnauthorizedException(
                    "Not signed in with Battle.net: sign in to import characters");
        }

        OAuth2AuthorizedClient client = authorizedClients.loadAuthorizedClient(
                REGISTRATION_ID, battleNet.getName());
        if (client == null) {
            throw new UnauthorizedException(
                    "No Battle.net token: sign in again to import characters");
        }
        OAuth2AccessToken token = client.getAccessToken();
        if (token == null || token.getExpiresAt() == null
                || !token.getExpiresAt().isAfter(Instant.now())) {
            throw new UnauthorizedException(
                    "The Battle.net token has expired: sign in again to import characters");
        }
        return token.getTokenValue();
    }
}
