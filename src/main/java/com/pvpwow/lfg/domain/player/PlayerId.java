package com.pvpwow.lfg.domain.player;

import java.util.Objects;
import java.util.UUID;

/**
 * Identity of a Player aggregate — the account that owns characters.
 */
public record PlayerId(UUID value) {

    public PlayerId {
        Objects.requireNonNull(value, "id must not be null");
    }
}
