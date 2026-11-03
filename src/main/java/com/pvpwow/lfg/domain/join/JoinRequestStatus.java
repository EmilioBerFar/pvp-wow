package com.pvpwow.lfg.domain.join;

/**
 * Lifecycle of a join request.
 *
 * <p>Five states, not two: a request nobody answered yet is not the same as
 * one that was refused, one that was accepted, one the requester withdrew and
 * one that died with its listing. Keeping them apart is what lets the metrics
 * tell "the owner did not answer" from "the owner said no".
 */
public enum JoinRequestStatus {
    /** Waiting for the listing owner's answer. */
    PENDING,
    /** The owner accepted: a group formed. */
    ACCEPTED,
    /** The owner refused. */
    REJECTED,
    /** The requester withdrew. */
    CANCELLED,
    /** The listing died before the owner answered. */
    EXPIRED
}
