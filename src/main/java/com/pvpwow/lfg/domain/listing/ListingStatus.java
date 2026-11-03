package com.pvpwow.lfg.domain.listing;

/**
 * Lifecycle of a listing.
 *
 * <p>Four states, not three: a listing that found a group is not the same as
 * one that aged out, and keeping them apart is what makes the success rate
 * meaningful. A listing the player pulled (CANCELLED) and one that found a
 * group (COMPLETED) both end the search, but only one of them is a success.
 */
public enum ListingStatus {
    ACTIVE,
    CANCELLED,
    EXPIRED,
    COMPLETED
}
