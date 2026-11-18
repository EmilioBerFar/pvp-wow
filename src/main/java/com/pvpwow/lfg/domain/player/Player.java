package com.pvpwow.lfg.domain.player;

import com.pvpwow.lfg.domain.shared.DomainRuleViolationException;

import java.util.Objects;
import java.util.UUID;

/**
 * The account that owns characters. Aggregate root.
 *
 * <p>Identity is the {@link PlayerId}, never the battle tag or the Battle.net
 * id: a player may rename their battle tag and must still be the same player.
 *
 * <p>Same two doors as {@link com.pvpwow.lfg.domain.character.Character}:
 * {@link #register} creates (identity generated inside), {@link #rehydrate}
 * reconstitutes from persisted state (identity received because it already
 * exists).
 *
 * <p>Since WU18 the account IS a Battle.net account, and the link is not
 * optional: a player exists because somebody logged in with Battle.net, and
 * the {@code sub} claim is what says who. There is no other door, which means
 * there is no way to reach a player row without an account behind it.
 */
public final class Player {

    private final PlayerId id;
    private final BattleNetId battleNetId;
    private final BattleTag battleTag;

    private Player(PlayerId id, BattleNetId battleNetId, BattleTag battleTag) {
        this.id = Objects.requireNonNull(id, "id must not be null");
        this.battleNetId = Objects.requireNonNull(battleNetId, "battleNetId must not be null");
        this.battleTag = Objects.requireNonNull(battleTag, "battleTag must not be null");
    }

    /**
     * Creates a player for a Battle.net account: the only way a player is born,
     * and the reason {@link BattleTag} is required.
     */
    public static Player register(BattleNetId battleNetId, BattleTag battleTag) {
        return new Player(new PlayerId(UUID.randomUUID()),
                Objects.requireNonNull(battleNetId, "battleNetId must not be null"),
                battleTag);
    }

    /**
     * Reconstitutes a player from persisted state.
     */
    public static Player rehydrate(PlayerId id, BattleNetId battleNetId, BattleTag battleTag) {
        return new Player(id,
                Objects.requireNonNull(battleNetId, "battleNetId must not be null"),
                battleTag);
    }

    public PlayerId id() {
        return id;
    }

    public BattleNetId battleNetId() {
        return battleNetId;
    }

    public BattleTag battleTag() {
        return battleTag;
    }

    /**
     * Records the tag the provider reports today. Battle.net lets a player
     * rename, and the board must keep showing the name they actually use.
     *
     * <p>Battle tags are NOT unique: Blizzard sells them and reclaims them, and
     * two accounts can carry the same one. So a rename never collides with
     * anybody, and it never needs to check.
     */
    public Player rename(BattleTag tag) {
        Objects.requireNonNull(tag, "battleTag must not be null");
        if (battleTag.equals(tag)) {
            return this;
        }
        return new Player(id, battleNetId, tag);
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
        return "Player[%s, %s]".formatted(id.value(), battleTag.value());
    }
}
