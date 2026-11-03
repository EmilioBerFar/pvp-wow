package com.pvpwow.lfg.domain.join;

import com.pvpwow.lfg.domain.character.CharacterId;
import com.pvpwow.lfg.domain.listing.ListingId;
import com.pvpwow.lfg.domain.shared.DomainRuleViolationException;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * A player's request to join somebody else's listing.
 *
 * <p>Why this exists at all: accepting unilaterally would close the owner's
 * listings without their consent, which lets anyone empty the board on
 * purpose and forge COMPLETED facts. The handshake is what makes COMPLETED
 * mean "both sides agreed" — and that is what makes the success rate
 * trustworthy.
 *
 * <p>A request LIVES AS LONG AS THE LISTING: {@code expiresAt} is the
 * listing's own expiration, not a separate TTL. A request to join a listing
 * that dies in three hours dies with it. There is no configuration for it,
 * because there is no second policy to configure.
 *
 * <p>State moves only through intention-revealing methods. Every transition
 * out of PENDING is one-shot: accepting twice, rejecting an accepted request
 * or cancelling a cancelled one is a caller bug, not a no-op.
 */
public final class JoinRequest {

    private final JoinRequestId id;
    private final CharacterId requesterId;
    private final ListingId listingId;
    private final Instant createdAt;
    private final Instant expiresAt;
    private JoinRequestStatus status;

    private JoinRequest(JoinRequestId id, CharacterId requesterId, ListingId listingId,
                        JoinRequestStatus status, Instant createdAt, Instant expiresAt) {
        this.id = Objects.requireNonNull(id, "id must not be null");
        this.requesterId = Objects.requireNonNull(requesterId, "requesterId must not be null");
        this.listingId = Objects.requireNonNull(listingId, "listingId must not be null");
        this.status = Objects.requireNonNull(status, "status must not be null");
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt must not be null");
        this.expiresAt = Objects.requireNonNull(expiresAt, "expiresAt must not be null");
        if (!expiresAt.isAfter(createdAt)) {
            throw new DomainRuleViolationException(
                    "A join request must expire after it was created, got: %s -> %s"
                            .formatted(createdAt, expiresAt));
        }
    }

    /**
     * Creates a PENDING request. The expiration is the listing's own: a
     * request to join a listing that dies in three hours dies with it.
     */
    public static JoinRequest request(CharacterId requesterId, ListingId listingId,
                                       Instant createdAt, Instant expiresAt) {
        Objects.requireNonNull(requesterId, "requesterId must not be null");
        Objects.requireNonNull(listingId, "listingId must not be null");
        Objects.requireNonNull(createdAt, "createdAt must not be null");
        Objects.requireNonNull(expiresAt, "expiresAt must not be null");
        return new JoinRequest(new JoinRequestId(UUID.randomUUID()), requesterId, listingId,
                JoinRequestStatus.PENDING, createdAt, expiresAt);
    }

    /** Reconstitutes a persisted request. */
    public static JoinRequest rehydrate(JoinRequestId id, CharacterId requesterId,
                                         ListingId listingId, JoinRequestStatus status,
                                         Instant createdAt, Instant expiresAt) {
        return new JoinRequest(id, requesterId, listingId, status, createdAt, expiresAt);
    }

    /**
     * The owner accepted: a group formed. Only from PENDING.
     */
    public void accept() {
        requirePending("accepted");
        status = JoinRequestStatus.ACCEPTED;
    }

    /**
     * The owner refused. Only from PENDING.
     */
    public void reject() {
        requirePending("rejected");
        status = JoinRequestStatus.REJECTED;
    }

    /**
     * The requester withdrew: it is no longer waiting for an answer. Only
     * from PENDING — a request that was already answered cannot be withdrawn.
     */
    public void cancel() {
        requirePending("cancelled");
        status = JoinRequestStatus.CANCELLED;
    }

    /**
     * The listing died before the owner answered. Idempotent by design:
     * whatever runs this may call it repeatedly.
     *
     * @return whether this call performed the transition
     */
    public boolean expireIfDue(Instant now) {
        Objects.requireNonNull(now, "now must not be null");
        if (status != JoinRequestStatus.PENDING || now.isBefore(expiresAt)) {
            return false;
        }
        status = JoinRequestStatus.EXPIRED;
        return true;
    }

    private void requirePending(String transition) {
        if (status != JoinRequestStatus.PENDING) {
            throw new DomainRuleViolationException(
                    "Only a pending join request can be %s, current status: %s"
                            .formatted(transition, status));
        }
    }

    public JoinRequestId id() {
        return id;
    }

    public CharacterId requesterId() {
        return requesterId;
    }

    public ListingId listingId() {
        return listingId;
    }

    public JoinRequestStatus status() {
        return status;
    }

    public Instant createdAt() {
        return createdAt;
    }

    public Instant expiresAt() {
        return expiresAt;
    }

    public boolean isPending() {
        return status == JoinRequestStatus.PENDING;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof JoinRequest request)) {
            return false;
        }
        return id.equals(request.id);
    }

    @Override
    public int hashCode() {
        return id.hashCode();
    }

    @Override
    public String toString() {
        return "JoinRequest[%s %s %s]".formatted(listingId, requesterId, status);
    }
}
