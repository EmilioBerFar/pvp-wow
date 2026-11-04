package com.pvpwow.lfg.adapters.in.rest;

import com.pvpwow.lfg.application.port.in.GetCurrentPlayer;
import com.pvpwow.lfg.domain.player.Player;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * "Who am I": the caller's own account.
 *
 * <p>There is no {@code GET /players/{id}} by design. A battle tag is personal
 * contact data, and an endpoint that resolves any id would let anybody harvest
 * them; the same anti-enumeration reasoning that hides other players'
 * characters applies here, but the answer is simpler — the only address is
 * "me".
 */
@RestController
@RequestMapping("/api/players")
public final class CurrentPlayerController {

    private final GetCurrentPlayer getCurrentPlayer;

    public CurrentPlayerController(GetCurrentPlayer getCurrentPlayer) {
        this.getCurrentPlayer = getCurrentPlayer;
    }

    @GetMapping("/me")
    PlayerResponse me() {
        return PlayerResponse.from(getCurrentPlayer.get());
    }

    record PlayerResponse(UUID id, String username, String battleTag) {

        static PlayerResponse from(Player player) {
            return new PlayerResponse(player.id().value(),
                    player.username().value(), player.battleTag().value());
        }
    }
}
