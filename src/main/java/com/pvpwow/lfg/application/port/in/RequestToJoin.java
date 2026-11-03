package com.pvpwow.lfg.application.port.in;

import com.pvpwow.lfg.domain.character.CharacterId;
import com.pvpwow.lfg.domain.join.JoinRequestId;
import com.pvpwow.lfg.domain.listing.ListingId;

import java.util.Objects;

/**
 * Driving port: ask to join somebody else's listing.
 *
 * <p>Asking closes nothing. The owner's answer is what closes listings, and
 * that is deliberate: accepting unilaterally would let anyone empty the
 * board on purpose and forge successes.
 */
public interface RequestToJoin {

    JoinRequestId request(RequestToJoinCommand command);

    record RequestToJoinCommand(CharacterId characterId, ListingId listingId) {

        public RequestToJoinCommand {
            Objects.requireNonNull(characterId, "characterId must not be null");
            Objects.requireNonNull(listingId, "listingId must not be null");
        }
    }
}
