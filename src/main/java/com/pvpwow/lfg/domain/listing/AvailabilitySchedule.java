package com.pvpwow.lfg.domain.listing;

import com.pvpwow.lfg.domain.shared.DomainRuleViolationException;

import java.time.ZoneId;
import java.util.Objects;
import java.util.Set;

/**
 * When a player is available, and in which time zone.
 *
 * <p>The zone is not decoration: without it "evenings" means nothing between
 * Madrid and Mexico City, and two players who both say "20:00" would never
 * meet. The windows stay in the player's own zone; converting to a common
 * reference is a query concern.
 *
 * <p>The {@link Set} type enforces the "no duplicate windows" rule by
 * construction — a stronger guarantee than a runtime check, because a duplicate
 * simply cannot get in.
 */
public record AvailabilitySchedule(Set<TimeWindow> windows, ZoneId zone) {

    public AvailabilitySchedule {
        Objects.requireNonNull(windows, "windows must not be null");
        Objects.requireNonNull(zone, "zone must not be null");
        if (windows.isEmpty()) {
            throw new DomainRuleViolationException(
                    "Availability schedule must declare at least one window");
        }
        windows = Set.copyOf(windows);
    }
}
