package com.pvpwow.lfg.application.service;

import com.pvpwow.lfg.application.error.ResourceNotFoundException;
import com.pvpwow.lfg.application.port.in.WithdrawJoinRequest;
import com.pvpwow.lfg.application.port.out.Characters;
import com.pvpwow.lfg.application.port.out.CurrentPlayerProvider;
import com.pvpwow.lfg.application.port.out.ListingLifecycle;
import com.pvpwow.lfg.domain.character.Character;
import com.pvpwow.lfg.domain.join.JoinRequest;
import com.pvpwow.lfg.domain.join.JoinRequestId;
import com.pvpwow.lfg.domain.player.PlayerId;
import com.pvpwow.lfg.domain.shared.DomainRuleViolationException;

import java.util.Objects;

/**
 * WithdrawJoinRequest, orchestrated. The requester takes their request back.
 *
 * <p>Only the requester may withdraw, and only their own: anybody else gets
 * the same 404 as for a request that does not exist, because the answer must
 * not reveal that it does.
 */
public final class WithdrawJoinRequestService implements WithdrawJoinRequest {

    private final Characters characters;
    private final ListingLifecycle lifecycle;
    private final CurrentPlayerProvider currentPlayer;

    public WithdrawJoinRequestService(Characters characters, ListingLifecycle lifecycle,
                                      CurrentPlayerProvider currentPlayer) {
        this.characters = Objects.requireNonNull(characters, "characters must not be null");
        this.lifecycle = Objects.requireNonNull(lifecycle, "lifecycle must not be null");
        this.currentPlayer = Objects.requireNonNull(currentPlayer, "currentPlayer must not be null");
    }

    @Override
    public void withdraw(JoinRequestId joinRequestId) {
        PlayerId caller = currentPlayer.currentPlayer();
        JoinRequest request = lifecycle.findJoinRequest(joinRequestId)
                .orElseThrow(() -> new ResourceNotFoundException("Join request not found"));

        Character requester = characters.findById(request.requesterId()).orElse(null);
        if (requester == null || !requester.ownerId().equals(caller)) {
            throw new ResourceNotFoundException("Join request not found");
        }
        if (!request.isPending()) {
            throw new DomainRuleViolationException(
                    "Join request is no longer pending, current status: %s".formatted(
                            request.status()));
        }

        request.cancel();
        lifecycle.save(request);
    }
}
