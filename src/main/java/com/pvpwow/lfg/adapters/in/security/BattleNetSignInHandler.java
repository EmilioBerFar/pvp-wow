package com.pvpwow.lfg.adapters.in.security;

import com.pvpwow.lfg.adapters.out.security.SessionCurrentPlayerProvider;
import com.pvpwow.lfg.application.port.in.SignInWithBattleNet;
import com.pvpwow.lfg.domain.player.BattleNetId;
import com.pvpwow.lfg.domain.player.BattleTag;
import com.pvpwow.lfg.domain.player.PlayerId;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * The bridge from "Battle.net says who you are" to "the board knows who you
 * are".
 *
 * <p>Spring finishes the authorization-code dance and hands us the provider's
 * userinfo. Two claims matter: {@code sub} (the account, stable) and {@code
 * battletag} (the name, renamable). The application turns that pair into a
 * {@link PlayerId} - creating, recovering or claiming the row - and the id
 * goes in the session, where {@link SessionCurrentPlayerProvider} reads it.
 *
 * <p>The response is a REDIRECT, not a body: this runs inside the filter
 * chain, where a controller advice does not reach. Sending the browser back to
 * the app is the only useful outcome; the app then calls the API with its new
 * session cookie.
 */
@Component
final class BattleNetSignInHandler implements AuthenticationSuccessHandler {

    private static final Logger log = LoggerFactory.getLogger(BattleNetSignInHandler.class);

    private static final String BATTLE_TAG_CLAIM = "battletag";
    private static final String FALLBACK_NAME_CLAIM = "preferred_username";

    private final SignInWithBattleNet signIn;
    private final String afterLogin;

    BattleNetSignInHandler(SignInWithBattleNet signIn,
                           @Value("${pvp.auth.after-login:/}") String afterLogin) {
        this.signIn = signIn;
        this.afterLogin = afterLogin;
    }

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request,
                                        HttpServletResponse response,
                                        Authentication authentication) throws IOException {
        OAuth2User user = (OAuth2User) authentication.getPrincipal();
        String account = stringClaim(user, "sub");
        String tag = stringClaim(user, BATTLE_TAG_CLAIM);
        if (tag == null) {
            tag = stringClaim(user, FALLBACK_NAME_CLAIM);
        }

        if (account == null || account.isBlank() || tag == null) {
            // A provider that answers without these claims cannot identify
            // anybody. Failing the login is the only honest answer: guessing
            // an account would be inventing identity.
            log.warn("Battle.net login without sub or battletag; refusing to sign in");
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED,
                    "Battle.net did not report an account id and a battle tag");
            return;
        }

        PlayerId playerId = signIn.signIn(
                new SignInWithBattleNet.Command(BattleNetId.parse(account),
                        new BattleTag(tag)));
        request.getSession(true).setAttribute(
                SessionCurrentPlayerProvider.PLAYER_ID_ATTRIBUTE, playerId.value());
        response.sendRedirect(afterLogin);
    }

    /**
     * Jackson binds a JSON number to an Integer and a JSON string to a String,
     * and Battle.net has used both shapes for {@code sub} over the years.
     * {@code String.valueOf} is the shortest honest way to accept either.
     */
    private static String stringClaim(OAuth2User user, String claim) {
        Object value = user.getAttributes().get(claim);
        return value == null ? null : String.valueOf(value);
    }
}
