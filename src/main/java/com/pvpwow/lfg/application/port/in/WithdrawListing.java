package com.pvpwow.lfg.application.port.in;

import com.pvpwow.lfg.domain.listing.ListingId;

import java.util.Objects;

/**
 * Driving port: the player pulls their own listing, by hand.
 *
 * <p>This is the CANCELLED path: the owner gave up on the search, as opposed
 * to finding a group (COMPLETED) or running out of time (EXPIRED).
 */
public interface WithdrawListing {

    void withdraw(WithdrawListingCommand command);

    record WithdrawListingCommand(ListingId listingId) {

        public WithdrawListingCommand {
            Objects.requireNonNull(listingId, "listingId must not be null");
        }
    }
}
