package com.pvpwow.lfg.application.port.in;

import com.pvpwow.lfg.domain.join.JoinRequestId;

import java.util.Objects;

/**
 * Driving port: the listing owner accepts a join request, and a group forms.
 *
 * <p>Accepting is the ONLY way a listing becomes COMPLETED, and it closes
 * every active listing of both players in one transaction, together with the
 * facts that record it.
 */
public interface AcceptJoinRequest {

    void accept(JoinRequestId joinRequestId);

    record AcceptJoinRequestCommand(JoinRequestId joinRequestId) {

        public AcceptJoinRequestCommand {
            Objects.requireNonNull(joinRequestId, "joinRequestId must not be null");
        }
    }
}
