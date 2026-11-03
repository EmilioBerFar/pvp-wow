package com.pvpwow.lfg.application.service;

import com.pvpwow.lfg.application.error.ResourceNotFoundException;
import com.pvpwow.lfg.application.port.in.WithdrawListing;
import com.pvpwow.lfg.application.port.in.WithdrawListing.WithdrawListingCommand;
import com.pvpwow.lfg.application.port.out.Characters;
import com.pvpwow.lfg.application.port.out.CurrentPlayerProvider;
import com.pvpwow.lfg.application.port.out.ListingLifecycle;
import com.pvpwow.lfg.domain.character.Character;
import com.pvpwow.lfg.domain.listing.LfgListing;
import com.pvpwow.lfg.domain.player.PlayerId;

import java.time.Clock;
import java.time.Instant;
import java.util.Objects;

/**
 * WithdrawListing, orchestrated. The owner pulls their own listing by hand:
 * it leaves the board and the fact records a CANCELLED.
 *
 * <p>Somebody else's listing is a 404, exactly like an absent one: the
 * caller must not learn whether it exists.
 */
public final class WithdrawListingService implements WithdrawListing {

    private final Characters characters;
    private final ListingLifecycle lifecycle;
    private final CurrentPlayerProvider currentPlayer;
    private final Clock clock;

    public WithdrawListingService(Characters characters, ListingLifecycle lifecycle,
                                  CurrentPlayerProvider currentPlayer, Clock clock) {
        this.characters = Objects.requireNonNull(characters, "characters must not be null");
        this.lifecycle = Objects.requireNonNull(lifecycle, "lifecycle must not be null");
        this.currentPlayer = Objects.requireNonNull(currentPlayer, "currentPlayer must not be null");
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
    }

    @Override
    public void withdraw(WithdrawListingCommand command) {
        PlayerId caller = currentPlayer.currentPlayer();
        LfgListing listing = lifecycle.findListing(command.listingId())
                .orElseThrow(() -> new ResourceNotFoundException("Listing not found"));

        Character owner = characters.findById(listing.characterId()).orElse(null);
        if (owner == null || !owner.ownerId().equals(caller)) {
            throw new ResourceNotFoundException("Listing not found");
        }

        Instant now = clock.instant();
        lifecycle.withdraw(listing.id(), owner.id(), now);
    }
}
