package com.pvpwow.lfg.domain.character;

import java.time.Instant;
import java.util.Objects;

/**
 * An OBSERVED snapshot of a character's gear: the item level and when it was
 * observed.
 *
 * <p>Item level and its date travel together on purpose. Two separate fields
 * (`itemLevel`, `itemLevelObservedAt`) would be two things to keep in sync by
 * hand, and the day someone forgets one they produce a character that claims
 * fresh gear forever. As a single value object, an item level without a date
 * cannot exist.
 */
public record GearSnapshot(ItemLevel itemLevel, Instant observedAt) {

    public GearSnapshot {
        Objects.requireNonNull(itemLevel, "itemLevel must not be null");
        Objects.requireNonNull(observedAt, "observedAt must not be null");
    }
}
