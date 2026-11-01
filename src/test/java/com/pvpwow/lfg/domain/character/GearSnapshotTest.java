package com.pvpwow.lfg.domain.character;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class GearSnapshotTest {

    private static final Instant OBSERVED_AT = Instant.parse("2026-11-01T10:00:00Z");

    @Test
    void couplesItemLevelAndObservationDate() {
        GearSnapshot gear = new GearSnapshot(new ItemLevel(675), OBSERVED_AT);

        assertThat(gear.itemLevel()).isEqualTo(new ItemLevel(675));
        assertThat(gear.observedAt()).isEqualTo(OBSERVED_AT);
    }

    @Test
    void anItemLevelWithoutADateCannotExist() {
        assertThatThrownBy(() -> new GearSnapshot(new ItemLevel(675), null))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new GearSnapshot(null, OBSERVED_AT))
                .isInstanceOf(NullPointerException.class);
    }
}
