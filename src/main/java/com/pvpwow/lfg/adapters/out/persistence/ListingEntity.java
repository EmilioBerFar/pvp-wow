package com.pvpwow.lfg.adapters.out.persistence;

import com.pvpwow.lfg.domain.character.Bracket;
import com.pvpwow.lfg.domain.character.Specialization;
import com.pvpwow.lfg.domain.listing.ListingStatus;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Relational shape of the LfgListing aggregate. The character relationship is
 * LAZY because ordinary listing writes only need its id; full characters are
 * hydrated by the dedicated JDBC board read adapter instead.
 */
@Entity
@Table(name = "lfg_listings")
class ListingEntity {

    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "character_id", nullable = false)
    private CharacterEntity character;

    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    private Bracket bracket;

    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    private Specialization spec;

    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    private ListingStatus status;

    @Column(name = "availability_zone", nullable = false)
    private String scheduleZone;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "listing_languages",
            joinColumns = @JoinColumn(name = "listing_id"))
    @Column(name = "language", nullable = false, length = 2)
    private Set<String> languages = new HashSet<>();

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "listing_windows",
            joinColumns = @JoinColumn(name = "listing_id"))
    private Set<WindowEmbeddable> windows = new HashSet<>();

    protected ListingEntity() {
        // JPA
    }

    UUID id() {
        return id;
    }

    CharacterEntity character() {
        return character;
    }

    Bracket bracket() {
        return bracket;
    }

    Specialization spec() {
        return spec;
    }

    ListingStatus status() {
        return status;
    }

    String scheduleZone() {
        return scheduleZone;
    }

    Instant createdAt() {
        return createdAt;
    }

    Instant expiresAt() {
        return expiresAt;
    }

    Set<String> languages() {
        return languages;
    }

    Set<WindowEmbeddable> windows() {
        return windows;
    }

    void setId(UUID id) {
        this.id = id;
    }

    void setCharacter(CharacterEntity character) {
        this.character = character;
    }

    void setBracket(Bracket bracket) {
        this.bracket = bracket;
    }

    void setSpec(Specialization spec) {
        this.spec = spec;
    }

    void setStatus(ListingStatus status) {
        this.status = status;
    }

    void setScheduleZone(String scheduleZone) {
        this.scheduleZone = scheduleZone;
    }

    void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    void setExpiresAt(Instant expiresAt) {
        this.expiresAt = expiresAt;
    }

    void setLanguages(Set<String> languages) {
        this.languages = languages;
    }

    void setWindows(Set<WindowEmbeddable> windows) {
        this.windows = windows;
    }
}
