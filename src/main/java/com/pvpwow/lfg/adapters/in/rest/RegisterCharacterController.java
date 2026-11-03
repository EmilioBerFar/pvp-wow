package com.pvpwow.lfg.adapters.in.rest;

import com.pvpwow.lfg.application.port.in.RegisterCharacter;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.util.UUID;

@RestController
@RequestMapping("/api/characters")
public final class RegisterCharacterController {

    private final RegisterCharacter registerCharacter;

    public RegisterCharacterController(RegisterCharacter registerCharacter) {
        this.registerCharacter = registerCharacter;
    }

    @PostMapping
    ResponseEntity<RegisteredCharacterResponse> register(
            @Valid @RequestBody RegisterCharacterRequest request) {
        UUID characterId = registerCharacter.register(request.toCommand()).value();
        URI location = UriComponentsBuilder.fromPath("/api/characters/{id}")
                .buildAndExpand(characterId).toUri();
        return ResponseEntity.created(location).body(new RegisteredCharacterResponse(characterId));
    }

    record RegisteredCharacterResponse(UUID id) {
    }
}
