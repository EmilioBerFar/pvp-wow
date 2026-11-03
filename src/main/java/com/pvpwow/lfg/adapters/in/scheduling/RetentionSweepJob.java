package com.pvpwow.lfg.adapters.in.scheduling;

import com.pvpwow.lfg.application.port.in.RunRetentionSweep;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * The clock that closes listings: a daily process, not a request.
 *
 * <p>WHY A SCHEDULED JOB AND NOT A LAZY CLEANUP: the board already hides
 * overdue rows, so correctness does not need this. What needs it is the
 * metrics and the storage: a listing that nobody touches would stay ACTIVE
 * for ever (never reaching the facts) and its row would never be deleted.
 * Hiding the symptom is not fixing the cause.
 *
 * <p>ONE INSTANCE, NO LOCK (documented assumption, not an oversight): two
 * instances running this job at the same instant would both try to close the
 * same rows. The partial unique index and the fact primary key would turn
 * that into an error and a rollback, not into duplicated history, but the
 * safe multi-instance design is a PostgreSQL advisory lock or
 * {@code FOR UPDATE SKIP LOCKED}. Until there is a second instance, a lock
 * would be machinery without a problem; the day there is one, it goes here.
 *
 * <p>The job logs its report at INFO, once per run: it is an operational
 * event, not a request. A failure propagates and Spring logs it; the pages
 * already committed stay committed, which is the point of the page size.
 */
@Component
final class RetentionSweepJob {

    private static final Logger log = LoggerFactory.getLogger(RetentionSweepJob.class);

    private final RunRetentionSweep sweep;

    RetentionSweepJob(RunRetentionSweep sweep) {
        this.sweep = sweep;
    }

    @Scheduled(cron = "${pvp.retention-cron}", zone = "UTC")
    void run() {
        RunRetentionSweep.SweepReport report = sweep.sweep();
        log.info("Retention sweep finished: expiredListings={} expiredRequests={} deletedListings={}",
                report.expiredListings(), report.expiredRequests(), report.deletedListings());
    }
}
