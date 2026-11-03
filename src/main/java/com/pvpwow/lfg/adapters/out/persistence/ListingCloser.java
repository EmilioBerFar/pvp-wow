package com.pvpwow.lfg.adapters.out.persistence;

import com.pvpwow.lfg.domain.character.Bracket;
import com.pvpwow.lfg.domain.character.CharacterId;
import com.pvpwow.lfg.domain.join.JoinRequestStatus;
import com.pvpwow.lfg.domain.listing.ListingOutcome;
import com.pvpwow.lfg.domain.listing.ListingId;
import com.pvpwow.lfg.domain.listing.ListingOutcomeKind;
import com.pvpwow.lfg.domain.listing.ListingStatus;
import jakarta.persistence.EntityManager;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * The ONE way a listing dies.
 *
 * <p>Every close — the owner pulling it, the group forming, the clock running
 * out, the sweep that frees the slot for a new publication — goes through
 * {@link #close}. That is the whole point of this class: a close that skips
 * it would write no fact (so the listing would never appear in the metrics)
 * and would leave its pending requests hanging (so the players who asked
 * would keep their slots occupied forever).
 *
 * <p>Those two failures are invisible in the UI: the board looks fine, the
 * listings are gone, and the numbers are quietly wrong. A single door makes
 * them impossible: there is nowhere else to close a listing. The two bulk
 * entry points below do not reimplement the close, they just loop over it.
 */
@Component
final class ListingCloser {

    private final ListingJpaRepository listings;
    private final JoinRequestJpaRepository joinRequests;
    private final ListingOutcomeJpaRepository outcomes;
    private final EntityManager entityManager;

    ListingCloser(ListingJpaRepository listings,
                  JoinRequestJpaRepository joinRequests,
                  ListingOutcomeJpaRepository outcomes,
                  EntityManager entityManager) {
        this.listings = listings;
        this.joinRequests = joinRequests;
        this.outcomes = outcomes;
        this.entityManager = entityManager;
    }

    /**
     * Closes ONE listing and everything that hangs from it: the fact that
     * records how it died, and the pending requests that were waiting for an
     * answer that can never come.
     *
     * <p>Does nothing when the listing is not ACTIVE: a listing that is
     * already dead has already written its fact.
     *
     * <p>The status UPDATE is flushed before returning, so the row stops
     * holding the partial unique slot at the exact instant the caller needs
     * the slot free.
     */
    ListingOutcome close(ListingEntity listing, ListingStatus status,
                         ListingOutcomeKind kind, Instant now) {
        if (listing.status() != ListingStatus.ACTIVE) {
            return outcomeOf(listing, kind, now);
        }
        listing.setStatus(status);
        entityManager.merge(listing);
        entityManager.flush();

        // The listing the requests were waiting for is gone, so the requests
        // are cancelled: they must not keep occupying the slots of the
        // players who sent them.
        joinRequests.cancelPendingForListing(listing.id(),
                JoinRequestStatus.PENDING, JoinRequestStatus.CANCELLED);

        ListingOutcome outcome = outcomeOf(listing, kind, now);
        outcomes.save(ListingOutcomeMapper.toEntity(outcome));
        entityManager.flush();
        return outcome;
    }

    /**
     * Closes every ACTIVE listing of a character, with its fact and its
     * requests. Used when a group forms: the search of both players ends.
     */
    List<ListingOutcome> closeAllOf(CharacterId characterId, ListingStatus status,
                                    ListingOutcomeKind kind, Instant now) {
        List<ListingEntity> active = listings.findByCharacterIdAndStatus(
                characterId.value(), ListingStatus.ACTIVE);
        List<ListingOutcome> closed = new ArrayList<>(active.size());
        for (ListingEntity listing : active) {
            closed.add(close(listing, status, kind, now));
        }
        return closed;
    }

    /**
     * The due listings of a character in one bracket, closed with their fact
     * and their requests. The sweep that frees the slot for a new
     * publication: without it, a listing that died here would never appear
     * in the metrics, because the retention job would find it already
     * EXPIRED and skip it.
     */
    int closeDue(CharacterId characterId, Bracket bracket, ListingStatus status,
                 ListingOutcomeKind kind, Instant now) {
        List<ListingEntity> due = listings.findDue(
                characterId.value(), bracket, ListingStatus.ACTIVE, now);
        for (ListingEntity listing : due) {
            close(listing, status, kind, now);
        }
        return due.size();
    }

    private static ListingOutcome outcomeOf(ListingEntity listing,
                                            ListingOutcomeKind kind, Instant now) {
        return new ListingOutcome(
                new ListingId(listing.id()),
                new CharacterId(listing.character().id()),
                listing.bracket(),
                listing.spec(),
                kind,
                listing.createdAt(),
                now);
    }
}
