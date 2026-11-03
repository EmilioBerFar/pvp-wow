package com.pvpwow.lfg.adapters.in.rest;

import com.pvpwow.lfg.application.port.in.WithdrawListing;
import com.pvpwow.lfg.application.port.in.WithdrawListing.WithdrawListingCommand;
import com.pvpwow.lfg.domain.listing.ListingId;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * The owner pulls their own listing: it leaves the board and the fact
 * records a CANCELLED.
 */
@RestController
@RequestMapping("/api/listings/{listingId}")
public final class WithdrawListingController {

    private final WithdrawListing withdrawListing;

    public WithdrawListingController(WithdrawListing withdrawListing) {
        this.withdrawListing = withdrawListing;
    }

    @DeleteMapping
    ResponseEntity<Void> withdraw(@PathVariable String listingId) {
        withdrawListing.withdraw(new WithdrawListingCommand(new ListingId(uuid(listingId))));
        return ResponseEntity.noContent().build();
    }

    private static UUID uuid(String value) {
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException malformed) {
            throw new MalformedIdentifierException("Malformed identifier");
        }
    }
}
