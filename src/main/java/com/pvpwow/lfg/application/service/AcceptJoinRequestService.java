package com.pvpwow.lfg.application.service;

import com.pvpwow.lfg.application.error.ResourceNotFoundException;
import com.pvpwow.lfg.application.port.in.AcceptJoinRequest;
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
 * AcceptJoinRequest, orchestrated. The owner accepts, a group forms, and
 * every active listing of BOTH players closes as COMPLETED with its fact —
 * in one transaction, because half a group is worse than no group.
 *
 * <p>Two rules protect the meaning of COMPLETED:
 *
 * <ul>
 *   <li>only the listing's owner may accept, and</li>
 *   <li>the owner may not accept their own request — it would not form a
 *       group and it would forge a success.</li>
 * </ul>
 */
public final class AcceptJoinRequestService implements AcceptJoinRequest {

    private final Characters characters;
    private final ListingLifecycle lifecycle;
    private final CurrentPlayerProvider currentPlayer;
    private final Clock clock;

    public AcceptJoinRequestService(Characters characters, ListingLifecycle lifecycle,
                                    CurrentPlayerProvider currentPlayer, Clock clock) {
        this.characters = Objects.requireNonNull(characters, "characters must not be null");
        this.lifecycle = Objects.requireNonNull(lifecycle, "lifecycle must not be null");
        this.currentPlayer = Objects.requireNonNull(currentPlayer, "currentPlayer must not be null");
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
    }

    @Override
    public void accept(JoinRequestId joinRequestId) {
        PlayerId caller = currentPlayer.currentPlayer();
        JoinRequest request = lifecycle.findJoinRequest(joinRequestId)
                .orElseThrow(() -> new ResourceNotFoundException("Join request not found"));

        Character owner = characters.findById(ownerOf(request)).orElse(null);
        if (owner == null || !owner.ownerId().equals(caller)) {
            // The requester could be anybody; the answer must not reveal
            // whether it exists to somebody who is not the owner.
            throw new ResourceNotFoundException("Join request not found");
        }

        if (!request.isPending()) {
            throw new DomainRuleViolationException(
                    "Join request is no longer pending, current status: %s".formatted(
                            request.status()));
        }

        Character requester = characters.findById(request.requesterId())
                .orElseThrow(ResourceNotFoundException::characterNotFound);
        if (requester.ownerId().equals(caller)) {
            throw new DomainRuleViolationException(
                    "You cannot accept your own listing");
        }

        Instant now = clock.instant();
        lifecycle.completeGroup(request, owner.id(), requester.id(), requester.ownerId(), now);
        // The aggregate's transition is persisted by the adapter, in the same
        // transaction as the closing of the listings and their facts.
    }

    /**
     * The owner of the listing the request points at. The request names the
     * listing, and the listing names its character: two hops through the
     * storage, because the request deliberately does not denormalize the
     * owner's character id.
     */
    private CharacterId ownerOf(JoinRequest request) {
        LfgListing listing = lifecycle.findListing(request.listingId())
                .orElseThrow(() -> new ResourceNotFoundException("Listing not found"));
        return listing.characterId();
    }
}
