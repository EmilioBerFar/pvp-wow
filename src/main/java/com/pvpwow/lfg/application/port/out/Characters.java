package com.pvpwow.lfg.application.port.out;

import com.pvpwow.lfg.domain.character.BlizzardCharacterId;
import com.pvpwow.lfg.domain.character.Character;
import com.pvpwow.lfg.domain.character.CharacterId;
import com.pvpwow.lfg.domain.character.Region;

import java.util.Optional;

/**
 * Driven port: character persistence. The interface speaks domain types
 * only; the JPA adapter (persistence step) translates at the edge.
 *
 * <p>HOW A CHARACTER IS FOUND: by the id the provider gave it. The natural
 * key - region, realm and name - is NOT stable (paid renames exist, and
 * realms move), so it cannot be what recognises a character. The
 * {@link BlizzardCharacterId} can, and that is why it is part of the
 * aggregate: a re-import finds the character it already imported instead of
 * duplicating it.
 */
public interface Characters {

    void save(Character character);

    Optional<Character> findById(CharacterId id);

    /**
     * The character Blizzard knows by this id, in this region, if we have it.
     *
     * <p>It may belong to another player: the id identifies the character in
     * the game, and the owner is a separate fact. The import has to ask this
     * question and then decide whether the character is ours to refresh.
     */
    Optional<Character> findByBlizzardId(Region region, BlizzardCharacterId blizzardId);
}
