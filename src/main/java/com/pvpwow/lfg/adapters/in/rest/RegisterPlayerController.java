package com.pvpwow.lfg.adapters.in.rest;

import com.pvpwow.lfg.application.port.in.RegisterPlayer;
import com.pvpwow.lfg.domain.player.PlayerId;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * Sign-up. Deliberately the only unauthenticated write: there is no identity
 * to present before the identity exists.
 *
 * <p>NO {@code Location} HEADER, unlike character registration. A player is
 * private — its battle tag is personal contact data — so there is no public
 * {@code /players/{id}} to point at. The only address of an account is
 * {@code /players/me}, and the body already carries the id.
 */
@RestController
@RequestMapping("/api/players")
public final class RegisterPlayerController {

    private final RegisterPlayer registerPlayer;

    public RegisterPlayerController(RegisterPlayer registerPlayer) {
        this.registerPlayer = registerPlayer;
    }

    @PostMapping
    ResponseEntity<RegisteredPlayerResponse> register(
            @Valid @RequestBody RegisterPlayerRequest request) {
        PlayerId id = registerPlayer.register(request.toCommand());
        return ResponseEntity.status(201).body(new RegisteredPlayerResponse(id.value()));
    }

    record RegisteredPlayerResponse(UUID id) {
    }
}
