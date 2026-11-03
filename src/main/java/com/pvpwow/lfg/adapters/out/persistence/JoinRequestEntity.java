package com.pvpwow.lfg.adapters.out.persistence;

import com.pvpwow.lfg.domain.character.Bracket;
import com.pvpwow.lfg.domain.character.Specialization;
import com.pvpwow.lfg.domain.join.JoinRequestStatus;
import com.pvpwow.lfg.domain.listing.ListingStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/**
 * Relational shape of the JoinRequest aggregate.
 *
 * <p>{@code ownerId} is denormalized on purpose: the rules about pending
 * requests are about the PLAYER (one request per listing per player, a
 * handful of open requests per player), and joining through the character
 * table on every check would be a needless hop. The character id is kept as
 * well: a request belongs to a character, and the facts are per character.
 */
@Entity
@Table(name = "join_requests")
class JoinRequestEntity {

    @Id
    private UUID id;

    @Column(name = "requester_id", nullable = false)
    private UUID requesterId;

    @Column(name = "owner_id", nullable = false)
    private UUID ownerId;

    @Column(name = "listing_id", nullable = false)
    private UUID listingId;

    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    private JoinRequestStatus status;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    protected JoinRequestEntity() {
        // JPA
    }

    UUID id() {
        return id;
    }

    UUID requesterId() {
        return requesterId;
    }

    UUID ownerId() {
        return ownerId;
    }

    UUID listingId() {
        return listingId;
    }

    JoinRequestStatus status() {
        return status;
    }

    Instant createdAt() {
        return createdAt;
    }

    Instant expiresAt() {
        return expiresAt;
    }

    void setId(UUID id) {
        this.id = id;
    }

    void setRequesterId(UUID requesterId) {
        this.requesterId = requesterId;
    }

    void setOwnerId(UUID ownerId) {
        this.ownerId = ownerId;
    }

    void setListingId(UUID listingId) {
        this.listingId = listingId;
    }

    void setStatus(JoinRequestStatus status) {
        this.status = status;
    }

    void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    void setExpiresAt(Instant expiresAt) {
        this.expiresAt = expiresAt;
    }
}
