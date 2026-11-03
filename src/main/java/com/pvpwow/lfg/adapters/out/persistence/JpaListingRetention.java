package com.pvpwow.lfg.adapters.out.persistence;

import com.pvpwow.lfg.application.port.out.ListingRetention;
import com.pvpwow.lfg.domain.join.JoinRequestStatus;
import com.pvpwow.lfg.domain.listing.ListingOutcomeKind;
import com.pvpwow.lfg.domain.listing.ListingStatus;
import jakarta.persistence.EntityManager;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * The retention sweep against PostgreSQL, one page per transaction.
 *
 * <p>WHY PAGES: a request closes one listing (or the handful a player has
 * active); the job walks the whole table. One transaction for the whole run
 * would hold locks for minutes and make a crash replay everything. A page is
 * a bounded unit of work: if page three fails, pages one and two are already
 * committed and the next run picks up where this one stopped.
 *
 * <p>WHY THE CLOSE GOES THROUGH {@link ListingCloser}: a listing that dies
 * here is a listing like any other. Going through the closer writes its fact
 * (so it reaches the metrics) and cancels its pending requests (so the
 * players who asked get their slots back). A bulk UPDATE would be faster and
 * would silently lose both, which is the bug it took a review to find.
 */
@Component
final class JpaListingRetention implements ListingRetention {

    private final ListingJpaRepository listings;
    private final JoinRequestJpaRepository joinRequests;
    private final ListingCloser closer;
    private final EntityManager entityManager;
    private final TransactionTemplate transactions;

    JpaListingRetention(ListingJpaRepository listings,
                        JoinRequestJpaRepository joinRequests,
                        ListingCloser closer,
                        EntityManager entityManager,
                        PlatformTransactionManager transactionManager) {
        this.listings = listings;
        this.joinRequests = joinRequests;
        this.closer = closer;
        this.entityManager = entityManager;
        this.transactions = new TransactionTemplate(transactionManager);
    }

    @Override
    public int expireOverdue(Instant now, int pageSize) {
        return transactions.execute(status -> {
            List<ListingEntity> due = listings.findOverdue(
                    ListingStatus.ACTIVE, now, PageRequest.of(0, pageSize));
            for (ListingEntity listing : due) {
                closer.close(listing, ListingStatus.EXPIRED, ListingOutcomeKind.EXPIRED, now);
            }
            return due.size();
        });
    }

    @Override
    public int expirePendingRequests(Instant now) {
        return transactions.execute(status -> joinRequests.expirePending(
                JoinRequestStatus.PENDING, JoinRequestStatus.EXPIRED, now));
    }

    @Override
    public int deleteClosedBefore(Instant cutoff, int pageSize) {
        return transactions.execute(status -> {
            List<UUID> ids = listings.findDeletableIds(
                    ListingStatus.ACTIVE, cutoff, PageRequest.of(0, pageSize));
            if (ids.isEmpty()) {
                return 0;
            }
            // The database cascades to join_requests, listing_languages and
            // listing_windows. The facts are untouched: no foreign key ties
            // them to the listing, and they are the only source of the
            // metrics — the listing itself is not history.
            int deleted = listings.deleteByIdIn(ids);
            entityManager.flush();
            return deleted;
        });
    }
}
