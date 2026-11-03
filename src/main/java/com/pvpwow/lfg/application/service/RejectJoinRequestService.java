package com.pvpwow.lfg.application.service;

import com.pvpwow.lfg.application.error.ResourceNotFoundException;
import com.pvpwow.lfg.application.port.in.RejectJoinRequest;
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

import java.util.Objects;

/**
 * RejectJoinRequest, orchestrated. The owner refuses: nothing closes, and
 * the listing keeps searching. REJECTED is not CANCELLED — the metrics must
 * be able to tell "the owner said no" from "the requester gave up".
 */
public final class RejectJoinRequestService implements RejectJoinRequest {

    private final Characters characters;
    private final ListingLifecycle lifecycle;
    private final CurrentPlayerProvider currentPlayer;

    public RejectJoinRequestService(Characters characters, ListingLifecycle lifecycle,
                                    CurrentPlayerProvider currentPlayer) {
        this.characters = Objects.requireNonNull(characters, "characters must not be null");
        this.lifecycle = Objects.requireNonNull(lifecycle, "lifecycle must not be null");
        this.currentPlayer = Objects.requireNonNull(currentPlayer, "currentPlayer must not be null");
    }

    @Override
    public void reject(JoinRequestId joinRequestId) {
        PlayerId caller = currentPlayer.currentPlayer();
        JoinRequest request = lifecycle.findJoinRequest(joinRequestId)
                .orElseThrow(() -> new ResourceNotFoundException("Join request not found"));

        Character owner = characters.findById(ownerOf(request)).orElse(null);
        if (owner == null || !owner.ownerId().equals(caller)) {
            throw new ResourceNotFoundException("Join request not found");
        }
        if (!request.isPending()) {
            throw new DomainRuleViolationException(
                    "Join request is no longer pending, current status: %s".formatted(
                            request.status()));
        }

        request.reject();
        lifecycle.save(request);
    }

    private CharacterId ownerOf(JoinRequest request) {
        LfgListing listing = lifecycle.findListing(request.listingId())
                .orElseThrow(() -> new ResourceNotFoundException("Listing not found"));
        return listing.characterId();
    }
}
