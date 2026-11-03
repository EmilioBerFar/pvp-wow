package com.pvpwow.lfg.application.port.in;

import com.pvpwow.lfg.domain.join.JoinRequestId;

import java.util.Objects;

/**
 * Driving port: the requester withdraws their own pending request.
 *
 * <p>Withdrawing is not rejecting: the owner never answered, and the metrics
 * must be able to tell those apart.
 */
public interface WithdrawJoinRequest {

    void withdraw(JoinRequestId joinRequestId);

    record WithdrawJoinRequestCommand(JoinRequestId joinRequestId) {

        public WithdrawJoinRequestCommand {
            Objects.requireNonNull(joinRequestId, "joinRequestId must not be null");
        }
    }
}
