package com.pvpwow.lfg.domain.player;

import java.util.Objects;
import java.util.UUID;

/**
 * The account that owns characters. Aggregate root.
 *
 * <p>Identity is the {@link PlayerId}, never the username or the battle tag:
 * a player may switch battle tag (or, later, log in through Battle.net OAuth)
 * and must still be the same player.
 *
 * <p>Same two doors as {@link com.pvpwow.lfg.domain.character.Character}:
 * {@link #register} creates (identity generated inside), {@link #rehydrate}
 * reconstitutes from persisted state (identity received because it already
 * exists).
 *
 * <p>Stub phase: there is no authentication yet. The battle tag is required
 * because it is the only contact channel in the product.
 */
public class Player {

    private final PlayerId id;
    private final Username username;
    private final BattleTag battleTag;

    private Player(PlayerId id, Username username, BattleTag battleTag) {
        this.id = Objects.requireNonNull(id, "id must not be null");
        this.username = Objects.requireNonNull(username, "username must not be null");
        this.battleTag = Objects.requireNonNull(battleTag, "battleTag must not be null");
    }

    /**
     * Creates a new player. Identity is generated here — callers never invent
     * ids.
     */
    public static Player register(Username username, BattleTag battleTag) {
        return new Player(new PlayerId(UUID.randomUUID()), username, battleTag);
    }

    /**
     * Reconstitutes a player from persisted state — the door repositories use.
     */
    public static Player rehydrate(PlayerId id, Username username, BattleTag battleTag) {
        return new Player(id, username, battleTag);
    }

    public PlayerId id() {
        return id;
    }

    public Username username() {
        return username;
    }

    public BattleTag battleTag() {
        return battleTag;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof Player player)) {
            return false;
        }
        return id.equals(player.id);
    }

    @Override
    public int hashCode() {
        return id.hashCode();
    }

    @Override
    public String toString() {
        return "Player[%s, %s]".formatted(username.value(), battleTag.value());
    }
}
