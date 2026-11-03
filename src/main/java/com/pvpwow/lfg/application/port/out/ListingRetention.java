package com.pvpwow.lfg.application.port.out;

import java.time.Instant;

/**
 * Driven port: the storage operations of the retention sweep.
 *
 * <p>WHY THE PAGE SIZE IS A PARAMETER: the job walks the whole table, not the
 * handful of rows a request touches. The port processes at most
 * {@code pageSize} rows per call, in its own transaction, and reports how many
 * it handled. The caller loops until a call returns less than a full page, so
 * a big backlog becomes many short commits instead of one long transaction
 * that locks the table and grows the WAL.
 *
 * <p>Every close inside {@link #expireOverdue} goes through the same closer
 * the rest of the application uses, so an expired listing always writes its
 * fact and cancels its pending requests. A sweep that flipped the status
 * directly would leave those listings out of the metrics for ever.
 */
public interface ListingRetention {

    /**
     * Closes the ACTIVE listings whose expiration has passed, oldest first.
     *
     * @return how many listings were closed in this page
     */
    int expireOverdue(Instant now, int pageSize);

    /**
     * Marks the PENDING requests that outlived their listing as EXPIRED.
     * Only the clock decides; no player action does this.
     *
     * @return how many requests changed status
     */
    int expirePendingRequests(Instant now);

    /**
     * Deletes the listings that are no longer ACTIVE and expired before
     * {@code cutoff}. Their facts stay: the fact table has no foreign key on
     * the listing, on purpose, and it is the ONLY source of the metrics.
     * Keeping the closed listings too would duplicate that source and let the
     * board table grow without a ceiling.
     *
     * <p>ACTIVE is the only status that survives: it is still on the board.
     *
     * @return how many listings were deleted in this page
     */
    int deleteClosedBefore(Instant cutoff, int pageSize);
}
