package com.pvpwow.lfg.adapters.out.security;

import com.pvpwow.lfg.application.port.out.CurrentPlayerProvider;
import com.pvpwow.lfg.domain.player.PlayerId;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.UUID;

/**
 * Dev stub: the current player is whoever the {@code X-Player-Id} header
 * claims. Battle.net OAuth later replaces ONLY this adapter — the port, the
 * use case and the domain never learn the difference.
 */
@Component
final class DevCurrentPlayerProvider implements CurrentPlayerProvider {

    private static final String PLAYER_ID_HEADER = "X-Player-Id";

    @Override
    public PlayerId currentPlayer() {
        HttpServletRequest request = currentRequest();
        String header = request.getHeader(PLAYER_ID_HEADER);
        if (header == null || header.isBlank()) {
            throw new UnauthorizedException("Missing X-Player-Id header");
        }
        try {
            return new PlayerId(UUID.fromString(header.trim()));
        } catch (IllegalArgumentException notAUuid) {
            throw new UnauthorizedException(
                    "X-Player-Id must be a UUID, got: '%s'".formatted(header));
        }
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
            return servletRequest.getRequest();
        }
        throw new UnauthorizedException(
                "No HTTP request context — cannot resolve the current player");
    }
}
