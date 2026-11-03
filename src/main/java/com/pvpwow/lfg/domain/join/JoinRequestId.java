package com.pvpwow.lfg.domain.join;

import java.util.Objects;
import java.util.UUID;

/** Identity of a join request. */
public record JoinRequestId(UUID value) {

    public JoinRequestId {
        Objects.requireNonNull(value, "value must not be null");
    }
}
