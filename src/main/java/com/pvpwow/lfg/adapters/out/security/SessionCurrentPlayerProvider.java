package com.pvpwow.lfg.adapters.out.security;

import com.pvpwow.lfg.application.error.UnauthorizedException;
import com.pvpwow.lfg.application.port.out.CurrentPlayerProvider;
import com.pvpwow.lfg.domain.player.PlayerId;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.Objects;
import java.util.UUID;

/**
 * The real adapter: the current player is whoever logged in with Battle.net
 * and left their player id in the session.
 *
 * <p>WHY THE SESSION AND NOT THE PRINCIPAL: Spring Security's principal
 * carries the Battle.net {@code sub}, which is an account id, not a player id.
 * Turning one into the other is a database read, and it already happened once
 * - at login, in the success handler. Repeating it on every request would be
 * the same lookup for the same answer. The session remembers the answer, and
 * invalidating the session forgets it, which is exactly what logout means.
 *
 * <p>This class and the login handler share {@link #PLAYER_ID_ATTRIBUTE}: one
 * writes it, this one reads it, and a typo in either would be a bug nobody
 * notices until a 401 appears out of nowhere.
 */
@Component
public class SessionCurrentPlayerProvider implements CurrentPlayerProvider {

    /**
     * The session attribute holding the logged-in {@link PlayerId} value.
     * Kept as a {@link UUID} so the session stays serializable.
     */
    public static final String PLAYER_ID_ATTRIBUTE = "pvp.current-player-id";

    @Override
    public PlayerId currentPlayer() {
        HttpServletRequest request = currentRequest();
        HttpSession session = request.getSession(false);
        Object attribute = session == null ? null : session.getAttribute(PLAYER_ID_ATTRIBUTE);
        if (attribute instanceof UUID playerId) {
            return new PlayerId(playerId);
        }
        // Reached only if a request slipped past the filter chain (a public
        // endpoint that still asks who is calling), or if the session was
        // dropped mid-request. The filter chain answers first in practice;
        // this is the contract, not the usual path.
        throw new UnauthorizedException(
                "Not signed in: authenticate with Battle.net first");
    }

    private HttpServletRequest currentRequest() {
        RequestAttributes attributes;
        try {
            attributes = RequestContextHolder.currentRequestAttributes();
        } catch (IllegalStateException noRequestBound) {
            throw new UnauthorizedException(
                    "No HTTP request context — cannot resolve the current player");
        }
        if (attributes instanceof ServletRequestAttributes servletRequest) {
            return Objects.requireNonNull(servletRequest.getRequest());
        }
        throw new UnauthorizedException(
                "No HTTP request context — cannot resolve the current player");
    }
}
