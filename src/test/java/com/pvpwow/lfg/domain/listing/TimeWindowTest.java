package com.pvpwow.lfg.domain.listing;

import com.pvpwow.lfg.domain.shared.DomainRuleViolationException;
import org.junit.jupiter.api.Test;

import java.time.DayOfWeek;
import java.time.LocalTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TimeWindowTest {

    @Test
    void holdsDayAndRange() {
        TimeWindow window = new TimeWindow(DayOfWeek.MONDAY,
                LocalTime.of(18, 0), LocalTime.of(22, 0));

        assertThat(window.day()).isEqualTo(DayOfWeek.MONDAY);
        assertThat(window.start()).isEqualTo(LocalTime.of(18, 0));
        assertThat(window.end()).isEqualTo(LocalTime.of(22, 0));
    }

    @Test
    void oneMinuteWindowIsValid() {
        assertThatNoException().isThrownBy(() -> new TimeWindow(DayOfWeek.FRIDAY,
                LocalTime.of(21, 59), LocalTime.of(22, 0)));
    }

    @Test
    void rejectsEmptyWindow() {
        assertThatThrownBy(() -> new TimeWindow(DayOfWeek.MONDAY,
                LocalTime.of(20, 0), LocalTime.of(20, 0)))
                .isInstanceOf(DomainRuleViolationException.class);
    }

    @Test
    void rejectsBackwardsWindowInsteadOfWrappingPastMidnight() {
        assertThatThrownBy(() -> new TimeWindow(DayOfWeek.SATURDAY,
                LocalTime.of(22, 0), LocalTime.of(2, 0)))
                .isInstanceOf(DomainRuleViolationException.class);
    }

    @Test
    void rejectsNullComponents() {
        assertThatThrownBy(() -> new TimeWindow(null, LocalTime.NOON, LocalTime.MAX))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new TimeWindow(DayOfWeek.MONDAY, null, LocalTime.MAX))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new TimeWindow(DayOfWeek.MONDAY, LocalTime.NOON, null))
                .isInstanceOf(NullPointerException.class);
    }
}
