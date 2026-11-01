package com.pvpwow.lfg.domain.character;

import com.pvpwow.lfg.domain.player.PlayerId;
import com.pvpwow.lfg.domain.shared.DomainRuleViolationException;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static java.util.stream.Collectors.toUnmodifiableSet;

/**
 * A player's character (toon) registered on the LFG board. Aggregate root.
 *
 * <p>Identity is the {@link CharacterId} — never the natural name+realm pair
 * (paid renames exist). Two Characters with the same id are the same
 * character even if every other field differs.
 *
 * <p>Lifecycle: exactly two doors, never more. {@link #register} creates
 * (identity generated inside); {@link #rehydrate} reconstitutes from
 * persisted state (identity received because it already exists). Behavior
 * methods that mutate state appear only when a real use case demands them —
 * none exists yet.
 */
public class Character {

    private final CharacterId id;
    private final PlayerId ownerId;
    private final CharacterName name;
    private final Realm realm;
    private final Region region;
    private final WowClass wowClass;
    private final GearSnapshot gear;
    private final Set<Specialization> specs;
    private final Map<Bracket, PvpStanding> standings;

    private Character(CharacterId id, PlayerId ownerId, CharacterName name, Realm realm,
                      Region region, WowClass wowClass, GearSnapshot gear,
                      Set<Specialization> specs, Map<Bracket, PvpStanding> standings) {
        this.id = Objects.requireNonNull(id, "id must not be null");
        this.ownerId = Objects.requireNonNull(ownerId, "ownerId must not be null");
        this.name = Objects.requireNonNull(name, "name must not be null");
        this.realm = Objects.requireNonNull(realm, "realm must not be null");
        this.region = Objects.requireNonNull(region, "region must not be null");
        this.wowClass = Objects.requireNonNull(wowClass, "wowClass must not be null");
        this.gear = Objects.requireNonNull(gear, "gear must not be null");
        Objects.requireNonNull(specs, "specs must not be null");
        Objects.requireNonNull(standings, "standings must not be null");
        this.specs = Set.copyOf(specs);
        this.standings = Map.copyOf(standings);
        ensureSpecsBelongToTheClass();
    }

    /**
     * Creates a new character. Identity is generated here — callers never
     * invent ids.
     */
    public static Character register(PlayerId ownerId, CharacterName name, Realm realm,
                                     Region region, WowClass wowClass, GearSnapshot gear,
                                     Set<Specialization> specs,
                                     Map<Bracket, PvpStanding> standings) {
        return new Character(new CharacterId(UUID.randomUUID()), ownerId, name, realm, region,
                wowClass, gear, specs, standings);
    }

    /**
     * Reconstitutes a character from persisted state — the door repositories
     * use. Identity is received, not generated, because it already exists.
     */
    public static Character rehydrate(CharacterId id, PlayerId ownerId, CharacterName name,
                                      Realm realm, Region region, WowClass wowClass,
                                      GearSnapshot gear, Set<Specialization> specs,
                                      Map<Bracket, PvpStanding> standings) {
        return new Character(id, ownerId, name, realm, region, wowClass, gear, specs, standings);
    }

    private void ensureSpecsBelongToTheClass() {
        if (specs.isEmpty()) {
            throw new DomainRuleViolationException(
                    "A character must declare at least one specialization, class: %s".formatted(wowClass));
        }
        Set<Specialization> playable = Specialization.forClass(wowClass);
        if (!playable.containsAll(specs)) {
            Set<Specialization> foreign = specs.stream()
                    .filter(spec -> !playable.contains(spec))
                    .collect(toUnmodifiableSet());
            throw new DomainRuleViolationException(
                    "Specializations %s do not belong to class %s".formatted(foreign, wowClass));
        }
    }

    // ---- queries: derived data is computed here, never stored ----

    public CharacterId id() {
        return id;
    }

    public PlayerId ownerId() {
        return ownerId;
    }

    public CharacterName name() {
        return name;
    }

    public Realm realm() {
        return realm;
    }

    public Region region() {
        return region;
    }

    public WowClass wowClass() {
        return wowClass;
    }

    /**
     * Convenience for the common read: the item level, without its date. Callers
     * that must show "as of" use {@link #gear()}.
     */
    public ItemLevel itemLevel() {
        return gear.itemLevel();
    }

    public GearSnapshot gear() {
        return gear;
    }

    public Set<Specialization> specs() {
        return specs;
    }

    public Map<Bracket, PvpStanding> standings() {
        return standings;
    }

    /**
     * The full observed fact for a bracket: rating, season and when it was seen.
     */
    public Optional<PvpStanding> standingFor(Bracket bracket) {
        return Optional.ofNullable(standings.get(bracket));
    }

    /**
     * The rating alone. {@code Optional} because an unranked bracket is a normal
     * state, not an error. Use {@link #standingFor} when the season or the
     * freshness matters — which, for a board that ages, is almost always.
     */
    public Optional<Rating> ratingFor(Bracket bracket) {
        return standingFor(bracket).map(PvpStanding::rating);
    }

    public boolean plays(Specialization spec) {
        return specs.contains(spec);
    }

    public Set<Role> roles() {
        return specs.stream().map(Specialization::role).collect(toUnmodifiableSet());
    }

    // ---- identity equality: the id IS the character ----

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof Character character)) {
            return false;
        }
        return id.equals(character.id);
    }

    @Override
    public int hashCode() {
        return id.hashCode();
    }

    @Override
    public String toString() {
        return "Character[%s-%s, %s %s]".formatted(name.value(), realm.value(), wowClass, specs);
    }
}
