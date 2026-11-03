package com.pvpwow.lfg.application.service;

import com.pvpwow.lfg.application.port.in.RunRetentionSweep.SweepReport;
import com.pvpwow.lfg.application.port.out.ListingRetention;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The sweep drains every page and the order is the rule: close, then expire
 * the leftover requests, then delete. Deleting before closing would remove a
 * row whose fact was never written.
 */
class RetentionSweepServiceTest {

    private static final Instant NOW = Instant.parse("2026-11-03T10:00:00Z");
    private static final Duration RETENTION = Duration.ofDays(30);
    private static final int PAGE_SIZE = 2;

    private final FakeRetention retention = new FakeRetention();
    private final RetentionSweepService service = new RetentionSweepService(
            retention, Clock.fixed(NOW, ZoneOffset.UTC), RETENTION, PAGE_SIZE);

    @Test
    void itClosesEveryPageUntilOneComesBackShort() {
        retention.overduePages = new int[]{2, 2, 1};

        SweepReport report = service.sweep();

        assertThat(report.expiredListings()).isEqualTo(5);
        assertThat(retention.overdueCalls)
                .as("two full pages and the short one that ends the run")
                .isEqualTo(3);
    }

    @Test
    void anExactFullPageTriggersOneMoreEmptyCallAndStops() {
        retention.overduePages = new int[]{2, 0};

        SweepReport report = service.sweep();

        assertThat(report.expiredListings()).isEqualTo(2);
        assertThat(retention.overdueCalls)
                .as("a full page does not prove there is more: the empty answer does")
                .isEqualTo(2);
    }

    @Test
    void theDeletionUsesTheWindowAnchoredOnNow() {
        retention.deletedPages = new int[]{1};

        service.sweep();

        assertThat(retention.expireNow).isEqualTo(NOW);
        assertThat(retention.requestNow).isEqualTo(NOW);
        assertThat(retention.cutoff)
                .as("only the rows that expired before now minus the window")
                .isEqualTo(NOW.minus(RETENTION));
    }

    @Test
    void theOrderIsCloseThenRequestsThenDelete() {
        service.sweep();

        assertThat(retention.calls)
                .containsExactly("expireOverdue", "expirePendingRequests", "deleteClosedBefore");
    }

    @Test
    void theReportCarriesWhatEachPassDid() {
        retention.overduePages = new int[]{2, 0};
        retention.expiredRequests = 3;
        retention.deletedPages = new int[]{4, 0};

        SweepReport report = service.sweep();

        assertThat(report.expiredListings()).isEqualTo(2);
        assertThat(report.expiredRequests()).isEqualTo(3);
        assertThat(report.deletedListings()).isEqualTo(4);
    }

    @Test
    void nothingToDoIsAnEmptyReport() {
        retention.overduePages = new int[]{0};
        retention.deletedPages = new int[]{0};

        SweepReport report = service.sweep();

        assertThat(report).isEqualTo(new SweepReport(0, 0, 0));
    }

    @Test
    void aZeroWindowIsRejectedAtConstruction() {
        assertThatThrownBy(() -> new RetentionSweepService(
                retention, Clock.systemUTC(), Duration.ZERO, PAGE_SIZE))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("positive");
    }

    @Test
    void aZeroPageSizeIsRejectedAtConstruction() {
        assertThatThrownBy(() -> new RetentionSweepService(
                retention, Clock.systemUTC(), RETENTION, 0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("positive");
    }

    /**
     * A hand-written double: no mocking framework, and the pages are scripted
     * so the test decides when the drain ends.
     */
    private static final class FakeRetention implements ListingRetention {

        private int[] overduePages = {0};
        private int[] deletedPages = {0};
        private int expiredRequests;
        private int overdueCalls;
        private int deleteCalls;
        private Instant expireNow;
        private Instant requestNow;
        private Instant cutoff;
        private final List<String> calls = new ArrayList<>();

        @Override
        public int expireOverdue(Instant now, int pageSize) {
            calls.add("expireOverdue");
            expireNow = now;
            int page = pageAt(overduePages, overdueCalls);
            overdueCalls++;
            return page;
        }

        @Override
        public int expirePendingRequests(Instant now) {
            calls.add("expirePendingRequests");
            requestNow = now;
            return expiredRequests;
        }

        @Override
        public int deleteClosedBefore(Instant cutoff, int pageSize) {
            calls.add("deleteClosedBefore");
            this.cutoff = cutoff;
            int page = pageAt(deletedPages, deleteCalls);
            deleteCalls++;
            return page;
        }

        private static int pageAt(int[] pages, int call) {
            return call < pages.length ? pages[call] : 0;
        }
    }
}
