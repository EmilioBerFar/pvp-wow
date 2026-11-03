package com.pvpwow.lfg.adapters.in.rest;

import com.pvpwow.lfg.application.error.ResourceNotFoundException;
import com.pvpwow.lfg.application.port.in.GetCharacter;
import com.pvpwow.lfg.domain.character.CharacterId;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * Reads one of the caller's own characters. This is also the resource that
 * {@code POST /api/characters} has been pointing at with its Location header
 * since WU8 — the header finally tells the truth.
 */
@RestController
@RequestMapping("/api/characters")
public final class GetCharacterController {

    private final GetCharacter getCharacter;

    public GetCharacterController(GetCharacter getCharacter) {
        this.getCharacter = getCharacter;
    }

    @GetMapping("/{characterId}")
    CharacterResponse get(@PathVariable String characterId) {
        return CharacterResponse.from(getCharacter.get(new CharacterId(uuid(characterId))));
    }

    private static UUID uuid(String value) {
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException malformed) {
            // A segment that is not a UUID cannot name a character either, so
            // it gets the same 404 and the same body. The anti-enumeration rule
            // stays total: no path reveals whether a character exists.
            throw ResourceNotFoundException.characterNotFound();
        }
    }
}
