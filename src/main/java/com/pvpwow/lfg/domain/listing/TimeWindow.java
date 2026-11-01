package com.pvpwow.lfg.domain.listing;

import com.pvpwow.lfg.domain.shared.DomainRuleViolationException;

import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.Objects;

/**
 * A weekly window of availability, in the player's own zone.
 *
 * <p>Half-open {@code [start, end)}: a window ending at 22:00 does NOT include
 * 22:00, so two adjacent windows (18:00-22:00 and 22:00-23:00) never overlap
 * and never leave the boundary hour unclaimed.
 *
 * <p>No midnight wrap in v1: {@code start} must be strictly before {@code end}.
 * A player available 22:00-02:00 declares two windows (22:00-23:59-ish and
 * 00:00-02:00) — an hour of precision nobody reads anyway, and the wrap would
 * turn every overlap check into a special case.
 */
public record TimeWindow(DayOfWeek day, LocalTime start, LocalTime end) {

    public TimeWindow {
        Objects.requireNonNull(day, "day must not be null");
        Objects.requireNonNull(start, "start must not be null");
        Objects.requireNonNull(end, "end must not be null");
        if (!start.isBefore(end)) {
            throw new DomainRuleViolationException(
                    "%s window must start before it ends, got: %s-%s".formatted(day, start, end));
        }
    }
}
