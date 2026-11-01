package com.pvpwow.lfg.domain.listing;

import com.pvpwow.lfg.domain.character.Bracket;
import com.pvpwow.lfg.domain.character.CharacterId;
import com.pvpwow.lfg.domain.character.Specialization;
import com.pvpwow.lfg.domain.shared.DomainRuleViolationException;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * A player's "looking for group" post: one character, one bracket, one pinned
 * specialization, the languages they speak and when they can play. Aggregate
 * root.
 *
 * <p>The spec is pinned at publish time on purpose: a listing says "I will play
 * Frost in 3v3 tonight", not "my character can play Mage". If the player later
 * changes spec, the listing must not silently change meaning.
 *
 * <p>Lifecycle: {@link #publish} creates, {@link #rehydrate} reconstitutes.
 * State moves only through intention-revealing methods ({@link #cancel},
 * {@link #expireIfDue}) — no setters.
 */
public class LfgListing {

    private final ListingId id;
    private final CharacterId characterId;
    private final Bracket bracket;
    private final Specialization spec;
    private final Set<Language> languages;
    private final AvailabilitySchedule schedule;
    private final Instant createdAt;
    private final Instant expiresAt;
    private ListingStatus status;

    private LfgListing(ListingId id, CharacterId characterId, Bracket bracket,
                       Specialization spec, Set<Language> languages,
                       AvailabilitySchedule schedule, ListingStatus status,
                       Instant createdAt, Instant expiresAt) {
        this.id = Objects.requireNonNull(id, "id must not be null");
        this.characterId = Objects.requireNonNull(characterId, "characterId must not be null");
        this.bracket = Objects.requireNonNull(bracket, "bracket must not be null");
        this.spec = Objects.requireNonNull(spec, "spec must not be null");
        Objects.requireNonNull(languages, "languages must not be null");
        this.schedule = Objects.requireNonNull(schedule, "schedule must not be null");
        this.status = Objects.requireNonNull(status, "status must not be null");
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt must not be null");
        this.expiresAt = Objects.requireNonNull(expiresAt, "expiresAt must not be null");
        if (languages.isEmpty()) {
            throw new DomainRuleViolationException(
                    "A listing must declare at least one language");
        }
        if (!expiresAt.isAfter(createdAt)) {
            throw new DomainRuleViolationException(
                    "A listing must expire after it was created, got: %s -> %s"
                            .formatted(createdAt, expiresAt));
        }
        this.languages = Set.copyOf(languages);
    }

    /**
     * Publishes a new listing. Identity is generated here; the listing starts
     * ACTIVE and dies at {@code now + ttl}.
     */
    public static LfgListing publish(CharacterId characterId, Bracket bracket,
                                     Specialization spec, Set<Language> languages,
                                     AvailabilitySchedule schedule, Instant now, Duration ttl) {
        Objects.requireNonNull(now, "now must not be null");
        Objects.requireNonNull(ttl, "ttl must not be null");
        if (ttl.isZero() || ttl.isNegative()) {
            throw new DomainRuleViolationException(
                    "Listing ttl must be positive, got: %s".formatted(ttl));
        }
        return new LfgListing(new ListingId(UUID.randomUUID()), characterId, bracket, spec,
                languages, schedule, ListingStatus.ACTIVE, now, now.plus(ttl));
    }

    /**
     * Reconstitutes a listing from persisted state — the door repositories
     * use. Persisted state can be in any status, so the status is received.
     */
    public static LfgListing rehydrate(ListingId id, CharacterId characterId, Bracket bracket,
                                       Specialization spec, Set<Language> languages,
                                       AvailabilitySchedule schedule, ListingStatus status,
                                       Instant createdAt, Instant expiresAt) {
        return new LfgListing(id, characterId, bracket, spec, languages, schedule, status,
                createdAt, expiresAt);
    }

    /**
     * The player pulls their own listing. Only an ACTIVE listing can be
     * cancelled — cancelling an expired or already cancelled one is a bug on
     * the caller's side, not a no-op.
     */
    public void cancel() {
        if (status != ListingStatus.ACTIVE) {
            throw new DomainRuleViolationException(
                    "Only an ACTIVE listing can be cancelled, current status: %s".formatted(status));
        }
        this.status = ListingStatus.CANCELLED;
    }

    /**
     * Expires the listing when due. Idempotent by design: whatever runs this
     * (a scheduled job, a query on read) may call it repeatedly.
     *
     * @return whether this call performed the transition
     */
    public boolean expireIfDue(Instant now) {
        Objects.requireNonNull(now, "now must not be null");
        if (status != ListingStatus.ACTIVE || now.isBefore(expiresAt)) {
            return false;
        }
        this.status = ListingStatus.EXPIRED;
        return true;
    }

    public boolean isActive() {
        return status == ListingStatus.ACTIVE;
    }

    public ListingId id() {
        return id;
    }

    public CharacterId characterId() {
        return characterId;
    }

    public Bracket bracket() {
        return bracket;
    }

    public Specialization spec() {
        return spec;
    }

    public Set<Language> languages() {
        return languages;
    }

    public AvailabilitySchedule schedule() {
        return schedule;
    }

    public ListingStatus status() {
        return status;
    }

    public Instant createdAt() {
        return createdAt;
    }

    public Instant expiresAt() {
        return expiresAt;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof LfgListing listing)) {
            return false;
        }
        return id.equals(listing.id);
    }

    @Override
    public int hashCode() {
        return id.hashCode();
    }

    @Override
    public String toString() {
        return "LfgListing[%s %s %s, %s]".formatted(bracket, spec, status, expiresAt);
    }
}
