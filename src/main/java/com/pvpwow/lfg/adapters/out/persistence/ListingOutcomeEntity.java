package com.pvpwow.lfg.adapters.out.persistence;

import com.pvpwow.lfg.domain.character.Bracket;
import com.pvpwow.lfg.domain.character.Specialization;
import com.pvpwow.lfg.domain.listing.ListingOutcomeKind;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/**
 * Relational shape of a ListingOutcome: the fact of a listing that will
 * never be visible again.
 *
 * <p>{@code characterId} is nullable ON DELETE SET NULL: the fact outlives
 * the character, because the metrics need the history even after the player
 * is gone.
 */
@Entity
@Table(name = "listing_outcomes")
class ListingOutcomeEntity {

    @Id
    @Column(name = "listing_id")
    private UUID listingId;

    @Column(name = "character_id")
    private UUID characterId;

    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    private Bracket bracket;

    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    private Specialization spec;

    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    private ListingOutcomeKind kind;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "closed_at", nullable = false)
    private Instant closedAt;

    protected ListingOutcomeEntity() {
        // JPA
    }

    UUID listingId() {
        return listingId;
    }

    UUID characterId() {
        return characterId;
    }

    Bracket bracket() {
        return bracket;
    }

    Specialization spec() {
        return spec;
    }

    ListingOutcomeKind kind() {
        return kind;
    }

    Instant createdAt() {
        return createdAt;
    }

    Instant closedAt() {
        return closedAt;
    }

    void setListingId(UUID listingId) {
        this.listingId = listingId;
    }

    void setCharacterId(UUID characterId) {
        this.characterId = characterId;
    }

    void setBracket(Bracket bracket) {
        this.bracket = bracket;
    }

    void setSpec(Specialization spec) {
        this.spec = spec;
    }

    void setKind(ListingOutcomeKind kind) {
        this.kind = kind;
    }

    void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    void setClosedAt(Instant closedAt) {
        this.closedAt = closedAt;
    }
}
