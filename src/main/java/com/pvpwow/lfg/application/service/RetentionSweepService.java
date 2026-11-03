package com.pvpwow.lfg.application.service;

import com.pvpwow.lfg.application.port.in.RunRetentionSweep;
import com.pvpwow.lfg.application.port.out.ListingRetention;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

/**
 * The retention sweep, orchestrated.
 *
 * <p>THREE PASSES, IN THIS ORDER: close what is overdue, expire the requests
 * that lost their listing, delete the closed rows past the retention window.
 * The order is not cosmetic:
 * <ul>
 *   <li>closing a listing cancels its pending requests as CANCELLED, so they
 *       must be closed before the request pass; the request pass is left with
 *       the ones whose listing died in an earlier run;</li>
 *   <li>the deletion only looks at CANCELLED and EXPIRED rows, so it must run
 *       after the close pass, never before: a row that is overdue but still
 *       ACTIVE would otherwise be deleted without its fact.</li>
 * </ul>
 *
 * <p>Everything is idempotent. Running the job twice, or crashing halfway,
 * only means the next run finds less to do.
 */
public final class RetentionSweepService implements RunRetentionSweep {

    /**
     * Rows per transaction. Big enough that the job is not chatty, small
     * enough that one page is a short transaction and a crash only replays
     * the page that failed.
     */
    public static final int DEFAULT_PAGE_SIZE = 200;

    private final ListingRetention retention;
    private final Clock clock;
    private final Duration retentionPeriod;
    private final int pageSize;

    public RetentionSweepService(ListingRetention retention, Clock clock,
                                 Duration retentionPeriod, int pageSize) {
        this.retention = Objects.requireNonNull(retention, "retention must not be null");
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
        Objects.requireNonNull(retentionPeriod, "retentionPeriod must not be null");
        if (retentionPeriod.isZero() || retentionPeriod.isNegative()) {
            throw new IllegalArgumentException(
                    "retentionPeriod must be positive, got: " + retentionPeriod);
        }
        if (pageSize <= 0) {
            throw new IllegalArgumentException("pageSize must be positive, got: " + pageSize);
        }
        this.retentionPeriod = retentionPeriod;
        this.pageSize = pageSize;
    }

    @Override
    public SweepReport sweep() {
        Instant now = clock.instant();

        // Close first: a listing that dies here writes its fact and cancels
        // its pending requests, exactly like a manual withdrawal.
        int expiredListings = drain(page -> retention.expireOverdue(now, page));

        // Then the requests whose listing died in an earlier run: nobody is
        // waiting for an answer that can never come.
        int expiredRequests = retention.expirePendingRequests(now);

        // And only now the deletion: everything being deleted is already
        // closed, so no fact is lost.
        int deletedListings = drain(page ->
                retention.deleteClosedBefore(now.minus(retentionPeriod), page));

        return new SweepReport(expiredListings, expiredRequests, deletedListings);
    }

    /**
     * Runs the pass page by page until a page comes back short. A full page
     * means "there may be more"; a short one means "this is the end".
     */
    private int drain(Page page) {
        int total = 0;
        int handled;
        do {
            handled = page.handle(pageSize);
            total += handled;
        } while (handled == pageSize);
        return total;
    }

    @FunctionalInterface
    private interface Page {
        int handle(int pageSize);
    }
}
