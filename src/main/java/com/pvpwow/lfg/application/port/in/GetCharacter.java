package com.pvpwow.lfg.application.port.in;

import com.pvpwow.lfg.domain.character.Character;
import com.pvpwow.lfg.domain.character.CharacterId;

/**
 * Driving port: read one of the caller's own characters.
 *
 * <p>Ownership is not a separate error. An unknown id and somebody else's
 * character produce the same 404, so this endpoint cannot be used to enumerate
 * other players' characters.
 */
public interface GetCharacter {

    Character get(CharacterId characterId);
}
