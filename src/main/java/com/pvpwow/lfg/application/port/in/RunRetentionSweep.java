package com.pvpwow.lfg.application.port.in;

/**
 * Driving port: the retention sweep. The clock, not a player, decides that a
 * listing is over.
 *
 * <p>It does three things, in this order, because the order is the rule:
 * <ol>
 *   <li>closes the listings that are past their expiration, through the same
 *       door every other close uses (fact + pending requests);</li>
 *   <li>marks the join requests that outlived their listing EXPIRED;</li>
 *   <li>deletes the closed rows that are older than the retention window.</li>
 * </ol>
 *
 * <p>Running it twice is harmless: a closed listing is no longer ACTIVE, a
 * deleted row is gone, and a request that is already EXPIRED is not PENDING.
 * That is what makes the job safe to retry after a crash.
 */
public interface RunRetentionSweep {

    SweepReport sweep();

    /**
     * What one sweep did. The operator reads it in the log; the tests read it
     * to prove the job drains every page instead of stopping at the first.
     */
    record SweepReport(int expiredListings, int expiredRequests, int deletedListings) {
    }
}
