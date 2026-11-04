package com.pvpwow.lfg.adapters.out.persistence;

import com.pvpwow.lfg.domain.player.BattleTag;
import com.pvpwow.lfg.domain.player.Player;
import com.pvpwow.lfg.domain.player.PlayerId;
import com.pvpwow.lfg.domain.player.Username;

/**
 * The only place where the player's columns become meaning. Both directions
 * are total, and {@code toDomain} always rehydrates: the id already exists in
 * the database, and a repository never invents identity.
 */
final class PlayerEntityMapper {

    private PlayerEntityMapper() {
    }

    static PlayerEntity toEntity(Player player) {
        PlayerEntity entity = new PlayerEntity();
        entity.setId(player.id().value());
        entity.setUsername(player.username().value());
        entity.setBattleTag(player.battleTag().value());
        return entity;
    }

    static Player toDomain(PlayerEntity entity) {
        return Player.rehydrate(
                new PlayerId(entity.id()),
                new Username(entity.username()),
                new BattleTag(entity.battleTag()));
    }
}
