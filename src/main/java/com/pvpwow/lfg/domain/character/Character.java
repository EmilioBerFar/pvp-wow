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
    private final ItemLevel itemLevel;
    private final Set<Specialization> specs;
    private final Map<Bracket, Rating> ratings;

    private Character(CharacterId id, PlayerId ownerId, CharacterName name, Realm realm,
                      Region region, WowClass wowClass, ItemLevel itemLevel,
                      Set<Specialization> specs, Map<Bracket, Rating> ratings) {
        this.id = Objects.requireNonNull(id, "id must not be null");
        this.ownerId = Objects.requireNonNull(ownerId, "ownerId must not be null");
        this.name = Objects.requireNonNull(name, "name must not be null");
        this.realm = Objects.requireNonNull(realm, "realm must not be null");
        this.region = Objects.requireNonNull(region, "region must not be null");
        this.wowClass = Objects.requireNonNull(wowClass, "wowClass must not be null");
        this.itemLevel = Objects.requireNonNull(itemLevel, "itemLevel must not be null");
        Objects.requireNonNull(specs, "specs must not be null");
        Objects.requireNonNull(ratings, "ratings must not be null");
        this.specs = Set.copyOf(specs);
        this.ratings = Map.copyOf(ratings);
        ensureSpecsBelongToTheClass();
    }

    /**
     * Creates a new character. Identity is generated here — callers never
     * invent ids.
     */
    public static Character register(PlayerId ownerId, CharacterName name, Realm realm,
                                     Region region, WowClass wowClass, ItemLevel itemLevel,
                                     Set<Specialization> specs, Map<Bracket, Rating> ratings) {
        return new Character(new CharacterId(UUID.randomUUID()), ownerId, name, realm, region,
                wowClass, itemLevel, specs, ratings);
    }

    /**
     * Reconstitutes a character from persisted state — the door repositories
     * use. Identity is received, not generated, because it already exists.
     */
    public static Character rehydrate(CharacterId id, PlayerId ownerId, CharacterName name,
                                      Realm realm, Region region, WowClass wowClass,
                                      ItemLevel itemLevel, Set<Specialization> specs,
                                      Map<Bracket, Rating> ratings) {
        return new Character(id, ownerId, name, realm, region, wowClass, itemLevel, specs, ratings);
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

    public ItemLevel itemLevel() {
        return itemLevel;
    }

    public Set<Specialization> specs() {
        return specs;
    }

    public Map<Bracket, Rating> ratings() {
        return ratings;
    }

    public Optional<Rating> ratingFor(Bracket bracket) {
        return Optional.ofNullable(ratings.get(bracket));
    }

    public boolean plays(Specialization spec) {
        return specs.contains(spec);
    }

    public Set<Role> roles() {
        return specs.stream().map(Specialization::role).collect(toUnmodifiableSet());
    }

    /**
     * Armory profile URL — fully derived from region + realm + name. Never
     * stored, never user-entered.
     *
     * <p>Known v1 limitation: Latin-script realms only. Cyrillic EU realms
     * (e.g. Гордунни) use transliterated slugs on the armory and would need
     * a transliteration step.
     */
    public String armoryUrl() {
        String locale = switch (region) {
            case EU -> "es-es";
            case US -> "en-us";
            case KR -> "ko-kr";
            case TW -> "zh-tw";
            case CN -> "zh-cn";
        };
        String realmSlug = realm.value().replace(" ", "-").replace("'", "");
        return "https://worldofwarcraft.blizzard.com/%s/character/%s/%s"
                .formatted(locale, realmSlug, name.value());
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
