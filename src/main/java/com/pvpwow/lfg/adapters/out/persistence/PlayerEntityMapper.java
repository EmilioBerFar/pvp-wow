package com.pvpwow.lfg.adapters.out.persistence;

import com.pvpwow.lfg.domain.player.BattleNetId;
import com.pvpwow.lfg.domain.player.BattleTag;
import com.pvpwow.lfg.domain.player.Player;
import com.pvpwow.lfg.domain.player.PlayerId;

/**
 * The only place where the player's columns become meaning. Both directions
 * are total, and {@code toDomain} always rehydrates: the id already exists in
 * the database, and a repository never invents identity.
 *
 * <p>The Battle.net id is the account, and it is never null: a player exists
 * because somebody logged in. Keeping that rule here means {@link Player}
 * never has to explain an absent account.
 */
final class PlayerEntityMapper {

    private PlayerEntityMapper() {
    }

    static PlayerEntity toEntity(Player player) {
        PlayerEntity entity = new PlayerEntity();
        entity.setId(player.id().value());
        entity.setBattleNetId(player.battleNetId().value());
        entity.setBattleTag(player.battleTag().value());
        return entity;
    }

    static Player toDomain(PlayerEntity entity) {
        return Player.rehydrate(new PlayerId(entity.id()),
                new BattleNetId(entity.battleNetId()),
                new BattleTag(entity.battleTag()));
    }
}
