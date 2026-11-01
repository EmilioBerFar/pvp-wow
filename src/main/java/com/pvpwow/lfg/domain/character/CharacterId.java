package com.pvpwow.lfg.domain.character;

import java.util.Objects;
import java.util.UUID;

/**
 * Identity of a Character aggregate. Two CharacterIds with the same UUID are
 * the same character — surrogate identity, never the natural name+realm pair
 * (paid renames exist).
 */
public record CharacterId(UUID value) {

    public CharacterId {
        Objects.requireNonNull(value, "id must not be null");
    }
}
