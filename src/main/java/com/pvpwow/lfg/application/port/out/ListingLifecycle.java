package com.pvpwow.lfg.application.port.out;

import com.pvpwow.lfg.domain.character.CharacterId;
import com.pvpwow.lfg.domain.join.JoinRequest;
import com.pvpwow.lfg.domain.join.JoinRequestId;
import com.pvpwow.lfg.domain.listing.LfgListing;
import com.pvpwow.lfg.domain.listing.ListingId;
import com.pvpwow.lfg.domain.listing.ListingOutcome;
import com.pvpwow.lfg.domain.player.PlayerId;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Driven port: the lifecycle of a listing and the join requests around it.
 *
 * <p>The atomic operations live here, not in the use case, because they are
 * operations on STORAGE: close several listings, write their facts, cancel
 * somebody's pending requests — all of it or none of it. Splitting them into
 * separate port methods would leave the atomicity as a convention of the
 * caller instead of a guarantee of the adapter.
 */
public interface ListingLifecycle {

    /**
     * Closes as COMPLETED every active listing of the listing owner and of the
     * requester, writes one fact per closed listing, cancels every PENDING
     * join request of BOTH players and marks the accepted request ACCEPTED.
     * ALL OR NOTHING.
     *
     * @return the facts of the closed listings
     */
    List<ListingOutcome> completeGroup(JoinRequest acceptedRequest,
                                        CharacterId ownerId,
                                        CharacterId requesterId,
                                        PlayerId ownerPlayerId,
                                        PlayerId requesterOwnerId,
                                        Instant now);

    /**
     * Closes one listing as CANCELLED (the owner pulled it) and writes its
     * fact. The PENDING requests aimed at that listing are cancelled too: the
     * listing they wanted no longer exists.
     */
    ListingOutcome withdraw(ListingId listingId, CharacterId characterId, Instant now);

    /**
     * Closes one listing as EXPIRED and writes its fact. Used by the retention
     * job, which is the only thing that marks a listing as dead by the clock.
     * The PENDING requests aimed at that listing expire with it.
     */
    ListingOutcome expire(ListingId listingId, CharacterId characterId, Instant now);

    /** The listing, if it exists. */
    Optional<LfgListing> findListing(ListingId listingId);

    /** The join request, if it exists. */
    Optional<JoinRequest> findJoinRequest(JoinRequestId joinRequestId);

    /** Persists a join request, or its new status if it already exists. */
    void save(JoinRequest request);

    /**
     * Whether the player already has a PENDING request for this listing. The
     * database index is the law; this is the friendly pre-check.
     */
    boolean hasPendingRequest(PlayerId ownerId, CharacterId requesterId, UUID listingId);

    /**
     * How many PENDING requests the player has open. The limit keeps a player
     * from carpet-bombing the board.
     *
     * <p>Only the requests that are still ALIVE count: a request whose
     * listing has died is expired by definition, even before the retention
     * job marks it. Counting it would block the player forever.
     */
    int pendingRequestCount(PlayerId ownerId, Instant now);
}
