package com.pvpwow.lfg.adapters.in.security;

import com.pvpwow.lfg.adapters.out.security.SessionCurrentPlayerProvider;
import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors
        .OAuth2LoginRequestPostProcessor;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.io.IOException;
import java.util.UUID;

/**
 * How a test pretends to be a signed-in player.
 *
 * <p>Two things, and they are two different things:
 *
 * <ul>
 *   <li>{@code oauth2Login()} says "the Battle.net handshake already
 *       happened": the filter chain sees a real {@code OAuth2AuthenticationToken}
 *       and lets the request through. It is the authentication half, and it is
 *       the same mechanism the production login produces, minus the network.</li>
 *   <li>The internal player id says "and the player that account belongs to is
 *       THIS ONE". Our adapter reads it from the session attribute, not from
 *       the principal, because the Battle.net {@code sub} is an account id and
 *       turning it into a player id is a database read that already happened at
 *       login time. A test cannot do that read without a Battle.net account,
 *       so it writes the answer the login handler would have written.</li>
 * </ul>
 *
 * <p>Both are needed: the first gets past the filter chain, the second gets
 * past the adapter. A test with only one of them is testing something nobody
 * ever does in production.
 *
 * <p>WHY THE PLAYER ID NEEDS A FILTER: Spring Session replaces the servlet
 * session with its own, and its filter runs before anything else. A request
 * post-processor runs BEFORE the filters, so the session it sees is MockMvc's
 * own {@code MockHttpSession} - a different session from the one the adapter
 * reads and from the one Spring Session persists. An attribute set there
 * would land in a session nobody ever looks at. A filter that runs after
 * Spring Session's own filter writes into the session that counts.
 *
 * <p>The id travels in a request attribute and the filter moves it into the
 * session, because a filter is global and the id changes per test.
 */
public final class AuthenticatedCaller {

    private static final String BATTLE_NET_SUBJECT = "123456789";

    /**
     * Where a test leaves the player id it wants in the session. The filter
     * picks it up from here.
     */
    public static final String REQUEST_PLAYER_ID = "pvp.test.player-id";

    private AuthenticatedCaller() {
    }

    /**
     * A request that is authenticated as a Battle.net user and whose session
     * names the given player.
     */
    public static MockHttpServletRequestBuilder signedIn(MockHttpServletRequestBuilder request,
                                                         UUID playerId) {
        return request.with(battleNetLogin())
                .with(playerIdInTheSession(playerId));
    }

    /**
     * A request that is authenticated as a Battle.net user but whose session
     * names no player: signed in with the provider, unknown to the board.
     */
    public static MockHttpServletRequestBuilder signedInWithoutAPlayer(
            MockHttpServletRequestBuilder request) {
        return request.with(battleNetLogin());
    }

    /**
     * Marks the request with the player id the session should end up naming.
     */
    public static org.springframework.test.web.servlet.request.RequestPostProcessor
            playerIdInTheSession(UUID playerId) {
        return request -> {
            request.setAttribute(REQUEST_PLAYER_ID, playerId);
            return request;
        };
    }

    /**
     * The OAuth2 login, with the claims Battle.net would report.
     */
    public static OAuth2LoginRequestPostProcessor battleNetLogin() {
        return SecurityMockMvcRequestPostProcessors.oauth2Login()
                .attributes(attributes -> {
                    attributes.put("sub", BATTLE_NET_SUBJECT);
                    attributes.put("battletag", "emilio#12345");
                });
    }

    /**
     * The filter that moves the marked player id into the session, and the
     * registration that puts it after Spring Session's filter.
     */
    @Configuration
    public static class PlayerIdWiring {

        @Bean
        FilterRegistrationBean<Filter> playerIdFilter() {
            FilterRegistrationBean<Filter> registration = new FilterRegistrationBean<>();
            registration.setFilter(new PlayerIdFilter());
            // Spring Session's own filter is Integer.MIN_VALUE + 50, and
            // Spring Security's chain is -100: anything between those two runs
            // after the session exists and before the chain authenticates.
            registration.setOrder(Ordered.HIGHEST_PRECEDENCE + 60);
            return registration;
        }
    }

    /**
     * Moves the marked player id into the session the request really uses.
     */
    public static final class PlayerIdFilter implements Filter {

        @Override
        public void doFilter(ServletRequest servletRequest,
                             ServletResponse servletResponse,
                             FilterChain chain) throws IOException, ServletException {
            HttpServletRequest request = (HttpServletRequest) servletRequest;
            Object playerId = request.getAttribute(REQUEST_PLAYER_ID);
            if (playerId instanceof UUID id) {
                request.getSession(true).setAttribute(
                        SessionCurrentPlayerProvider.PLAYER_ID_ATTRIBUTE, id);
            }
            chain.doFilter(servletRequest, servletResponse);
        }
    }
}
