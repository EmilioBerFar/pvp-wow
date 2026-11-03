package com.pvpwow.lfg.domain.listing;

/**
 * How a listing ended. The three kinds answer three different questions:
 * did it find a group, did the owner give up, did it run out of time.
 */
public enum ListingOutcomeKind {

    /** A group formed: both sides agreed. */
    COMPLETED,
    /** The owner pulled it, by hand. */
    CANCELLED,
    /** It ran out of time. */
    EXPIRED
}
