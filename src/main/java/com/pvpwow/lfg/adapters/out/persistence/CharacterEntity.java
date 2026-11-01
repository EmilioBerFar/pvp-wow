package com.pvpwow.lfg.adapters.out.persistence;

import com.pvpwow.lfg.domain.character.Bracket;
import com.pvpwow.lfg.domain.character.Region;
import com.pvpwow.lfg.domain.character.Specialization;
import com.pvpwow.lfg.domain.character.WowClass;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.MapKeyColumn;
import jakarta.persistence.MapKeyEnumerated;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Relational shape of the Character aggregate. Deliberately mutable, public
 * to nobody, and NOT the domain type: the package law says domain objects
 * never leak here as-is, so this class is a translation target and nothing
 * else. All invariants live in the domain; this entity only knows columns.
 *
 * <p>The two collections are EAGER on purpose: a rehydrated aggregate missing
 * its specs or standings is a lie, and the board reads whole characters
 * anyway. If a future bulk query suffers, add a projection — never a partial
 * domain object.
 */
@Entity
@Table(name = "characters")
class CharacterEntity {

    @Id
    private UUID id;

    @Column(name = "owner_id", nullable = false)
    private UUID ownerId;

    @Column(nullable = false, length = 12)
    private String name;

    @Column(nullable = false)
    private String realm;

    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    private Region region;

    @Column(name = "wow_class", nullable = false)
    @Enumerated(EnumType.STRING)
    private WowClass wowClass;

    @Column(name = "item_level", nullable = false)
    private int itemLevel;

    @Column(name = "gear_observed_at", nullable = false)
    private Instant gearObservedAt;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "character_specs",
            joinColumns = @JoinColumn(name = "character_id"))
    @Column(name = "spec", nullable = false)
    @Enumerated(EnumType.STRING)
    private Set<Specialization> specs = new HashSet<>();

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "character_standings",
            joinColumns = @JoinColumn(name = "character_id"))
    @MapKeyEnumerated(EnumType.STRING)
    @MapKeyColumn(name = "bracket", nullable = false)
    private Map<Bracket, StandingEmbeddable> standings = new HashMap<>();

    protected CharacterEntity() {
        // JPA
    }

    UUID id() {
        return id;
    }

    UUID ownerId() {
        return ownerId;
    }

    String name() {
        return name;
    }

    String realm() {
        return realm;
    }

    Region region() {
        return region;
    }

    WowClass wowClass() {
        return wowClass;
    }

    int itemLevel() {
        return itemLevel;
    }

    Instant gearObservedAt() {
        return gearObservedAt;
    }

    Set<Specialization> specs() {
        return specs;
    }

    Map<Bracket, StandingEmbeddable> standings() {
        return standings;
    }

    void setId(UUID id) {
        this.id = id;
    }

    void setOwnerId(UUID ownerId) {
        this.ownerId = ownerId;
    }

    void setName(String name) {
        this.name = name;
    }

    void setRealm(String realm) {
        this.realm = realm;
    }

    void setRegion(Region region) {
        this.region = region;
    }

    void setWowClass(WowClass wowClass) {
        this.wowClass = wowClass;
    }

    void setItemLevel(int itemLevel) {
        this.itemLevel = itemLevel;
    }

    void setGearObservedAt(Instant gearObservedAt) {
        this.gearObservedAt = gearObservedAt;
    }

    void setSpecs(Set<Specialization> specs) {
        this.specs = specs;
    }

    void setStandings(Map<Bracket, StandingEmbeddable> standings) {
        this.standings = standings;
    }
}
