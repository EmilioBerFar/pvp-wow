package com.pvpwow.lfg.application.service;

import com.pvpwow.lfg.application.error.ResourceNotFoundException;
import com.pvpwow.lfg.application.port.in.RequestToJoin;
import com.pvpwow.lfg.application.port.in.RequestToJoin.RequestToJoinCommand;
import com.pvpwow.lfg.application.port.out.Characters;
import com.pvpwow.lfg.application.port.out.CurrentPlayerProvider;
import com.pvpwow.lfg.application.port.out.ListingLifecycle;
import com.pvpwow.lfg.domain.character.Character;
import com.pvpwow.lfg.domain.character.CharacterId;
import com.pvpwow.lfg.domain.join.JoinRequest;
import com.pvpwow.lfg.domain.join.JoinRequestId;
import com.pvpwow.lfg.domain.listing.LfgListing;
import com.pvpwow.lfg.domain.player.PlayerId;
import com.pvpwow.lfg.domain.shared.DomainRuleViolationException;

import java.time.Clock;
import java.time.Instant;
import java.util.Objects;

/**
 * RequestToJoin, orchestrated. Asking closes nothing: the owner's answer is
 * what closes listings, and that is what makes COMPLETED mean "both sides
 * agreed".
 */
public final class RequestToJoinService implements RequestToJoin {

    /**
     * How many pending requests one player may have open at a time. Without a
     * limit a player could carpet-bomb the board; with a single global
     * pending request a player who found no answer would be blocked for the
     * whole life of the listing (24 hours).
     */
    static final int MAX_PENDING_REQUESTS = 3;

    private final Characters characters;
    private final ListingLifecycle lifecycle;
    private final CurrentPlayerProvider currentPlayer;
    private final Clock clock;

    public RequestToJoinService(Characters characters, ListingLifecycle lifecycle,
                                 CurrentPlayerProvider currentPlayer, Clock clock) {
        this.characters = Objects.requireNonNull(characters, "characters must not be null");
        this.lifecycle = Objects.requireNonNull(lifecycle, "lifecycle must not be null");
        this.currentPlayer = Objects.requireNonNull(currentPlayer, "currentPlayer must not be null");
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
    }

    @Override
    public JoinRequestId request(RequestToJoinCommand command) {
        PlayerId caller = currentPlayer.currentPlayer();
        Character requester = characters.findById(command.characterId())
                .orElseThrow(ResourceNotFoundException::characterNotFound);
        if (!requester.ownerId().equals(caller)) {
            // Same message and status as an absent id, on purpose.
            throw ResourceNotFoundException.characterNotFound();
        }

        LfgListing listing = lifecycle.findListing(command.listingId())
                .orElseThrow(() -> new ResourceNotFoundException("Listing not found"));
        if (listing.characterId().equals(requester.id())) {
            throw new DomainRuleViolationException(
                    "You cannot request to join your own listing");
        }

        Instant now = clock.instant();
        if (!listing.isActiveAt(now)) {
            throw new DomainRuleViolationException(
                    "Listing %s is no longer open for requests".formatted(listing.id()));
        }

        if (lifecycle.hasPendingRequest(caller, requester.id(), command.listingId().value())) {
            throw new DomainRuleViolationException(
                    "You already have a pending request for this listing");
        }
        if (lifecycle.pendingRequestCount(caller, now) >= MAX_PENDING_REQUESTS) {
            throw new DomainRuleViolationException(
                    "You already have %d pending requests (the limit is %d)"
                            .formatted(lifecycle.pendingRequestCount(caller, now),
                                    MAX_PENDING_REQUESTS));
        }

        // A request lives exactly as long as the listing it asks for: no
        // separate TTL, no separate configuration.
        JoinRequest request = JoinRequest.request(requester.id(), listing.id(), now,
                listing.expiresAt());
        lifecycle.save(request);
        return request.id();
    }
}
