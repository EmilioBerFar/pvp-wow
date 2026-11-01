package com.pvpwow.lfg.domain.listing;

/**
 * Lifecycle of a listing.
 *
 * <p>Three states, not two: a listing the player pulled is not the same as one
 * that aged out. Keeping them apart preserves intent for future features
 * (re-publish a cancelled listing vs let an expired one go).
 */
public enum ListingStatus {
    ACTIVE,
    CANCELLED,
    EXPIRED
}
