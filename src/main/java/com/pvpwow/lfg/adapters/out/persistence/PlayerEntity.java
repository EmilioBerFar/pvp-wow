package com.pvpwow.lfg.adapters.out.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.util.UUID;

/**
 * Relational shape of the Player aggregate. Three columns, and that is the
 * whole account: the aggregate has no temporal state, so neither does the
 * table. A {@code created_at} "just a case" would be a column nobody reads.
 *
 * <p>{@code battleNetId} is a {@code String}, not a number: it stores the
 * {@code sub} claim verbatim, because that is the shape OpenID Connect defines
 * and the shape the provider sends. The column is NOT NULL because a player
 * exists only because somebody logged in with Battle.net.
 *
 * <p>{@code battleTag} is deliberately not unique: Blizzard reclaims battle
 * tags and two accounts can carry the same one, so it is contact data the
 * product shows, never an identity.
 */
@Entity
@Table(name = "players")
class PlayerEntity {

    @Id
    private UUID id;

    @Column(name = "battle_net_id", nullable = false)
    private String battleNetId;

    @Column(name = "battle_tag", nullable = false, length = 64)
    private String battleTag;

    protected PlayerEntity() {
        // JPA
    }

    UUID id() {
        return id;
    }

    String battleNetId() {
        return battleNetId;
    }

    String battleTag() {
        return battleTag;
    }

    void setId(UUID id) {
        this.id = id;
    }

    void setBattleNetId(String battleNetId) {
        this.battleNetId = battleNetId;
    }

    void setBattleTag(String battleTag) {
        this.battleTag = battleTag;
    }
}
