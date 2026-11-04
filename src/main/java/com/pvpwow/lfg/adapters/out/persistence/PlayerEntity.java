package com.pvpwow.lfg.adapters.out.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.util.UUID;

/**
 * Relational shape of the Player aggregate. Three columns, and that is the
 * whole account: the aggregate has no temporal state, so neither does the
 * table. A {@code created_at} "just in case" would be a column nobody reads.
 */
@Entity
@Table(name = "players")
class PlayerEntity {

    @Id
    private UUID id;

    @Column(nullable = false, length = 24)
    private String username;

    @Column(name = "battle_tag", nullable = false, length = 64)
    private String battleTag;

    protected PlayerEntity() {
        // JPA
    }

    UUID id() {
        return id;
    }

    String username() {
        return username;
    }

    String battleTag() {
        return battleTag;
    }

    void setId(UUID id) {
        this.id = id;
    }

    void setUsername(String username) {
        this.username = username;
    }

    void setBattleTag(String battleTag) {
        this.battleTag = battleTag;
    }
}
