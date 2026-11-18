package com.pvpwow.lfg.adapters.in.security;

import com.pvpwow.lfg.adapters.out.security.SessionCurrentPlayerProvider;
import com.pvpwow.lfg.application.port.in.SignInWithBattleNet;
import com.pvpwow.lfg.domain.player.BattleNetId;
import com.pvpwow.lfg.domain.player.BattleTag;
import com.pvpwow.lfg.domain.player.PlayerId;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The bridge: claims in, session out. No network, no filter chain - the two
 * decisions this class makes are "what identifies the account" and "where the
 * answer is remembered".
 */
class BattleNetSignInHandlerTest {

    private final RecordingSignIn signIn = new RecordingSignIn();
    private final BattleNetSignInHandler handler = new BattleNetSignInHandler(signIn, "/");

    @Test
    void signsInWithTheSubClaimAndPutsThePlayerInTheSession() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();

        handler.onAuthenticationSuccess(request, response, authentication(Map.of(
                "sub", "123456789",
                "battletag", "Emilio#12345")));

        assertThat(signIn.account.get().value()).isEqualTo("123456789");
        assertThat(signIn.tag.get().value()).isEqualTo("emilio#12345");
        assertThat(request.getSession(false)
                .getAttribute(SessionCurrentPlayerProvider.PLAYER_ID_ATTRIBUTE))
                .isEqualTo(signIn.playerId.value());
        assertThat(response.getRedirectedUrl()).isEqualTo("/");
    }

    @Test
    void acceptsANumericSubBecauseJacksonMayBindItAsANumber() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();

        handler.onAuthenticationSuccess(new MockHttpServletRequest(), response, authentication(Map.of(
                "sub", 123456789,
                "battletag", "Emilio#12345")));

        assertThat(signIn.account.get().value()).isEqualTo("123456789");
        assertThat(response.getStatus()).isEqualTo(302);
    }

    @Test
    void keepsTheSubVerbatimEvenWhenItIsNotANumber() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();

        handler.onAuthenticationSuccess(new MockHttpServletRequest(), response, authentication(Map.of(
                "sub", "not-a-number",
                "battletag", "Emilio#12345")));

        assertThat(signIn.account.get().value()).isEqualTo("not-a-number");
        assertThat(response.getStatus()).isEqualTo(302);
    }

    @Test
    void fallsBackToPreferredUsernameWhenTheTagClaimIsMissing() throws Exception {
        handler.onAuthenticationSuccess(new MockHttpServletRequest(),
                new MockHttpServletResponse(), authentication(Map.of(
                        "sub", "42",
                        "preferred_username", "Emilio#12345")));

        assertThat(signIn.tag.get().value()).isEqualTo("emilio#12345");
    }

    @Test
    void refusesToSignInWithoutAnAccountOrATag() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();

        // The principal is built on "battletag" here only because Spring
        // refuses to construct a user whose name attribute is absent; the
        // handler reads "sub" and finds nothing.
        handler.onAuthenticationSuccess(new MockHttpServletRequest(), response,
                authentication(Map.of("battletag", "Emilio#12345"), "battletag"));

        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(signIn.account.get()).isNull();
    }

    @Test
    void refusesToSignInWhenTheSubClaimIsBlank() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();

        handler.onAuthenticationSuccess(new MockHttpServletRequest(), response,
                authentication(Map.of(
                        "sub", "   ",
                        "battletag", "Emilio#12345")));

        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(signIn.account.get()).isNull();
    }

    private static TestingAuthenticationToken authentication(Map<String, Object> attributes) {
        return authentication(attributes, "sub");
    }

    private static TestingAuthenticationToken authentication(Map<String, Object> attributes,
                                                             String nameAttributeKey) {
        Map<String, Object> claims = new HashMap<>(attributes);
        DefaultOAuth2User user = new DefaultOAuth2User(
                AuthorityUtils.createAuthorityList("ROLE_USER"), claims, nameAttributeKey);
        return new TestingAuthenticationToken(user, null, "ROLE_USER");
    }

    private static final class RecordingSignIn implements SignInWithBattleNet {

        private final PlayerId playerId = new PlayerId(UUID.randomUUID());
        private final AtomicReference<BattleNetId> account = new AtomicReference<>();
        private final AtomicReference<BattleTag> tag = new AtomicReference<>();

        @Override
        public PlayerId signIn(Command command) {
            account.set(command.battleNetId());
            tag.set(command.battleTag());
            return playerId;
        }
    }
}
