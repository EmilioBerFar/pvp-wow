package com.pvpwow.lfg.domain.listing;

import java.util.Objects;
import java.util.UUID;

/**
 * Identity of an LfgListing aggregate.
 */
public record ListingId(UUID value) {

    public ListingId {
        Objects.requireNonNull(value, "id must not be null");
    }
}
