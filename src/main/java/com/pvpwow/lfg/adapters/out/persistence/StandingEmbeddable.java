package com.pvpwow.lfg.adapters.out.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

import java.time.Instant;

/**
 * One row of {@code character_standings}: the bracket comes from the map key,
 * these three columns are the observed fact itself.
 */
@Embeddable
class StandingEmbeddable {

    @Column(name = "rating", nullable = false)
    private int rating;

    @Column(name = "season_id", nullable = false)
    private int seasonId;

    @Column(name = "observed_at", nullable = false)
    private Instant observedAt;

    protected StandingEmbeddable() {
        // JPA
    }

    static StandingEmbeddable of(int rating, int seasonId, Instant observedAt) {
        StandingEmbeddable standing = new StandingEmbeddable();
        standing.rating = rating;
        standing.seasonId = seasonId;
        standing.observedAt = observedAt;
        return standing;
    }

    int rating() {
        return rating;
    }

    int seasonId() {
        return seasonId;
    }

    Instant observedAt() {
        return observedAt;
    }
}
