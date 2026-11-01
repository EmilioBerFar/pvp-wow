package com.pvpwow.lfg.domain.character;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PvpStandingTest {

    private static final Instant OBSERVED_AT = Instant.parse("2026-11-01T10:00:00Z");

    @Test
    void couplesRatingSeasonAndObservationDate() {
        PvpStanding standing = new PvpStanding(new Rating(1800), new PvpSeason(42), OBSERVED_AT);

        assertThat(standing.rating().value()).isEqualTo(1800);
        assertThat(standing.season().id()).isEqualTo(42);
        assertThat(standing.observedAt()).isEqualTo(OBSERVED_AT);
    }

    @Test
    void twoStandingsForDifferentSeasonsAreDifferentFacts() {
        assertThat(new PvpStanding(new Rating(1800), new PvpSeason(3), OBSERVED_AT))
                .isNotEqualTo(new PvpStanding(new Rating(1800), new PvpSeason(4), OBSERVED_AT));
    }

    @Test
    void rejectsNullComponents() {
        assertThatThrownBy(() -> new PvpStanding(null, new PvpSeason(42), OBSERVED_AT))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new PvpStanding(new Rating(1800), null, OBSERVED_AT))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new PvpStanding(new Rating(1800), new PvpSeason(42), null))
                .isInstanceOf(NullPointerException.class);
    }
}
