package com.pvpwow.lfg.adapters.out.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;

import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.Objects;

/**
 * One row of {@code listing_windows}. Equality is by all three values because
 * the field is a Set and Hibernate uses equality to detect collection changes.
 */
@Embeddable
class WindowEmbeddable {

    @Column(name = "day_of_week", nullable = false)
    @Enumerated(EnumType.STRING)
    private DayOfWeek dayOfWeek;

    @Column(name = "start_time", nullable = false)
    private LocalTime startTime;

    @Column(name = "end_time", nullable = false)
    private LocalTime endTime;

    protected WindowEmbeddable() {
        // JPA
    }

    static WindowEmbeddable of(DayOfWeek dayOfWeek, LocalTime startTime, LocalTime endTime) {
        WindowEmbeddable window = new WindowEmbeddable();
        window.dayOfWeek = dayOfWeek;
        window.startTime = startTime;
        window.endTime = endTime;
        return window;
    }

    DayOfWeek dayOfWeek() {
        return dayOfWeek;
    }

    LocalTime startTime() {
        return startTime;
    }

    LocalTime endTime() {
        return endTime;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof WindowEmbeddable window)) {
            return false;
        }
        return dayOfWeek == window.dayOfWeek
                && startTime.equals(window.startTime)
                && endTime.equals(window.endTime);
    }

    @Override
    public int hashCode() {
        return Objects.hash(dayOfWeek, startTime, endTime);
    }
}
