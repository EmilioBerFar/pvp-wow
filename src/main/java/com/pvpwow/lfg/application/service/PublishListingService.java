package com.pvpwow.lfg.application.service;

import com.pvpwow.lfg.application.error.ResourceNotFoundException;
import com.pvpwow.lfg.application.port.in.PublishListing;
import com.pvpwow.lfg.application.port.in.PublishListing.PublishListingCommand;
import com.pvpwow.lfg.application.port.out.Characters;
import com.pvpwow.lfg.application.port.out.CurrentPlayerProvider;
import com.pvpwow.lfg.application.port.out.Listings;
import com.pvpwow.lfg.domain.character.Character;
import com.pvpwow.lfg.domain.listing.LfgListing;
import com.pvpwow.lfg.domain.listing.ListingId;
import com.pvpwow.lfg.domain.player.PlayerId;
import com.pvpwow.lfg.domain.shared.DomainRuleViolationException;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

/**
 * PublishListing, orchestrated. The aggregate owns its own lifecycle rules;
 * this service owns the rules that require more than one aggregate: identity,
 * ownership, spec membership and active-listing uniqueness.
 */
public final class PublishListingService implements PublishListing {

    /**
     * One message for an unknown character and for a character owned by
     * somebody else. Distinguishing them would let a caller enumerate other
     * players' characters (OWASP anti-enumeration).
     */
    private static final String CHARACTER_NOT_FOUND = "Character not found";

    private final Characters characters;
    private final Listings listings;
    private final CurrentPlayerProvider currentPlayer;
    private final Clock clock;
    private final Duration listingTtl;

    public PublishListingService(Characters characters, Listings listings,
                                 CurrentPlayerProvider currentPlayer, Clock clock,
                                 Duration listingTtl) {
        this.characters = Objects.requireNonNull(characters, "characters must not be null");
        this.listings = Objects.requireNonNull(listings, "listings must not be null");
        this.currentPlayer = Objects.requireNonNull(currentPlayer, "currentPlayer must not be null");
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
        this.listingTtl = Objects.requireNonNull(listingTtl, "listingTtl must not be null");
    }

    @Override
    public ListingId publish(PublishListingCommand command) {
        PlayerId caller = currentPlayer.currentPlayer();
        Character character = characters.findById(command.characterId())
                .orElseThrow(() -> new ResourceNotFoundException(CHARACTER_NOT_FOUND));

        if (!character.ownerId().equals(caller)) {
            // Same message and status as an absent id, on purpose.
            throw new ResourceNotFoundException(CHARACTER_NOT_FOUND);
        }
        if (!character.plays(command.spec())) {
            throw new DomainRuleViolationException(
                    "Character %s does not play %s".formatted(
                            character.name().value(), command.spec()));
        }

        Instant now = clock.instant();
        if (listings.hasActiveFor(character.id(), command.bracket(), now)) {
            throw new DomainRuleViolationException(
                    "Character %s already has an active %s listing".formatted(
                            character.name().value(), command.bracket()));
        }

        LfgListing listing = LfgListing.publish(character.id(), command.bracket(), command.spec(),
                command.languages(), command.schedule(), now, listingTtl);
        listings.publish(listing);
        return listing.id();
    }
}
