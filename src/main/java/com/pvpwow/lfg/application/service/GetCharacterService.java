package com.pvpwow.lfg.application.service;

import com.pvpwow.lfg.application.error.ResourceNotFoundException;
import com.pvpwow.lfg.application.port.in.GetCharacter;
import com.pvpwow.lfg.application.port.out.Characters;
import com.pvpwow.lfg.application.port.out.CurrentPlayerProvider;
import com.pvpwow.lfg.domain.character.Character;
import com.pvpwow.lfg.domain.character.CharacterId;
import com.pvpwow.lfg.domain.player.PlayerId;

import java.util.Objects;

/**
 * GetCharacter, orchestrated. The caller is resolved FIRST, exactly as in
 * publish: an unauthenticated request is a 401 whether or not the id exists.
 */
public final class GetCharacterService implements GetCharacter {

    private final Characters characters;
    private final CurrentPlayerProvider currentPlayer;

    public GetCharacterService(Characters characters, CurrentPlayerProvider currentPlayer) {
        this.characters = Objects.requireNonNull(characters, "characters must not be null");
        this.currentPlayer = Objects.requireNonNull(currentPlayer, "currentPlayer must not be null");
    }

    @Override
    public Character get(CharacterId characterId) {
        Objects.requireNonNull(characterId, "characterId must not be null");
        PlayerId caller = currentPlayer.currentPlayer();

        Character character = characters.findById(characterId)
                .orElseThrow(ResourceNotFoundException::characterNotFound);
        if (!character.ownerId().equals(caller)) {
            // Deliberately identical to the absent-id branch.
            throw ResourceNotFoundException.characterNotFound();
        }
        return character;
    }
}
