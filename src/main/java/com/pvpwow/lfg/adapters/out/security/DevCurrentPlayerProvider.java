package com.pvpwow.lfg.adapters.out.security;

import com.pvpwow.lfg.application.error.UnauthorizedException;
import com.pvpwow.lfg.application.port.out.CurrentPlayerProvider;
import com.pvpwow.lfg.application.port.out.Players;
import com.pvpwow.lfg.domain.player.PlayerId;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.Objects;
import java.util.UUID;

/**
 * Dev stub: the current player is whoever the {@code X-Player-Id} header
 * claims — PROVIDED that player exists. A header is a claim; a claim that
 * names no player is not an identity.
 *
 * <p>WHY THE ADAPTER READS THE DATABASE: authentication must guarantee that
 * the identity it returns is real. If the header were taken at face value, an
 * unknown id would travel through every use case and only fail at the foreign
 * key, as a 500. Validating here means every caller downstream receives a
 * genuine owner, and a typo in the header is a 401 with a readable message.
 * The cost is one primary-key lookup per request, which the index turns into
 * a single page read.
 *
 * <p>Battle.net OAuth (next work unit) replaces ONLY this adapter — the port,
 * the use cases and the domain never learn the difference.
 */
@Component
final class DevCurrentPlayerProvider implements CurrentPlayerProvider {

    private static final String PLAYER_ID_HEADER = "X-Player-Id";

    private final Players players;

    DevCurrentPlayerProvider(Players players) {
        this.players = Objects.requireNonNull(players, "players must not be null");
    }

    @Override
    public PlayerId currentPlayer() {
        PlayerId id = declaredId();
        if (players.findById(id).isEmpty()) {
            // Not "user not found": the caller controls this header, so naming
            // the problem leaks nothing that is not already theirs.
            throw new UnauthorizedException(
                    "Unknown player: register one with POST /api/players first");
        }
        return id;
    }

    private PlayerId declaredId() {
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
