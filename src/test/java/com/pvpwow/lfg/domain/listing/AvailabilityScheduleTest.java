package com.pvpwow.lfg.domain.listing;

import com.pvpwow.lfg.domain.shared.DomainRuleViolationException;
import org.junit.jupiter.api.Test;

import java.time.DayOfWeek;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.HashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AvailabilityScheduleTest {

    private static final ZoneId MADRID = ZoneId.of("Europe/Madrid");

    @Test
    void holdsWindowsAndZone() {
        Set<TimeWindow> windows = Set.of(evening(DayOfWeek.MONDAY));

        AvailabilitySchedule schedule = new AvailabilitySchedule(windows, MADRID);

        assertThat(schedule.windows()).containsExactly(evening(DayOfWeek.MONDAY));
        assertThat(schedule.zone()).isEqualTo(MADRID);
    }

    @Test
    void rejectsAnEmptySchedule() {
        assertThatThrownBy(() -> new AvailabilitySchedule(Set.of(), MADRID))
                .isInstanceOf(DomainRuleViolationException.class);
    }

    @Test
    void duplicateWindowsCollapseIntoOne() {
        Set<TimeWindow> withDuplicates = new HashSet<>();
        withDuplicates.add(evening(DayOfWeek.MONDAY));
        withDuplicates.add(evening(DayOfWeek.MONDAY));

        assertThat(new AvailabilitySchedule(withDuplicates, MADRID).windows()).hasSize(1);
    }

    @Test
    void copiesDefensivelySoTheCallerCannotMutateIt() {
        Set<TimeWindow> windows = new HashSet<>(Set.of(evening(DayOfWeek.MONDAY)));
        AvailabilitySchedule schedule = new AvailabilitySchedule(windows, MADRID);

        windows.add(evening(DayOfWeek.TUESDAY));

        assertThat(schedule.windows()).hasSize(1);
        assertThatThrownBy(() -> schedule.windows().add(evening(DayOfWeek.WEDNESDAY)))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void rejectsNullComponents() {
        assertThatThrownBy(() -> new AvailabilitySchedule(null, MADRID))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new AvailabilitySchedule(Set.of(evening(DayOfWeek.MONDAY)), null))
                .isInstanceOf(NullPointerException.class);
    }

    private TimeWindow evening(DayOfWeek day) {
        return new TimeWindow(day, LocalTime.of(18, 0), LocalTime.of(22, 0));
    }
}
