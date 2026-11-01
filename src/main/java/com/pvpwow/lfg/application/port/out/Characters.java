package com.pvpwow.lfg.application.port.out;

import com.pvpwow.lfg.domain.character.Character;
import com.pvpwow.lfg.domain.character.CharacterName;
import com.pvpwow.lfg.domain.character.Realm;
import com.pvpwow.lfg.domain.character.Region;

/**
 * Driven port: character persistence. The interface speaks domain types
 * only; the JPA adapter (persistence step) translates at the edge.
 */
public interface Characters {

    void save(Character character);

    /**
     * Friendly duplicate pre-check for (region, realm, name) — a character's
     * identity in WoW. Advisory by nature: two simultaneous registrations can
     * race past it until the persistence step adds the unique index. The
     * index is the law; this check is UX.
     */
    boolean alreadyRegistered(Region region, Realm realm, CharacterName name);
}
