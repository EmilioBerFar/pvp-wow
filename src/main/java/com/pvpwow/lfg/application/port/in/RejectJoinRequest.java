package com.pvpwow.lfg.application.port.in;

import com.pvpwow.lfg.domain.join.JoinRequestId;

import java.util.Objects;

/**
 * Driving port: the listing owner refuses a join request. Nothing closes.
 */
public interface RejectJoinRequest {

    void reject(JoinRequestId joinRequestId);

    record RejectJoinRequestCommand(JoinRequestId joinRequestId) {

        public RejectJoinRequestCommand {
            Objects.requireNonNull(joinRequestId, "joinRequestId must not be null");
        }
    }
}
