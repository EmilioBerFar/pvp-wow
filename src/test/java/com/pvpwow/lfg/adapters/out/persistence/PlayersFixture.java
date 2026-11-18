package com.pvpwow.lfg.adapters.out.persistence;

import com.pvpwow.lfg.application.port.out.Players;
import com.pvpwow.lfg.domain.player.BattleNetId;
import com.pvpwow.lfg.domain.player.BattleTag;
import com.pvpwow.lfg.domain.player.Player;
import com.pvpwow.lfg.domain.player.PlayerId;

/**
 * WU17 gave characters.owner_id a real foreign key, so an IT that inserts a
 * character must insert its owner first. This helper derives a battle tag from
 * the id itself: the tests care about the OWNER RELATION, not about inventing
 * believable identity, and a deterministic derivation cannot collide with a
 * hand-written fixture.
 *
 * <p>The Battle.net id is derived from the player id for the same reason, and
 * because a player cannot exist without one any more.
 */
final class PlayersFixture {

    private PlayersFixture() {
    }

    static PlayerId ensure(Players players, PlayerId id) {
        if (players.findById(id).isEmpty()) {
            String handle = "u" + id.value().toString().replace("-", "").substring(0, 8);
            players.save(Player.rehydrate(id,
                    new BattleNetId(id.value().toString()),
                    new BattleTag(handle + "#1234")));
        }
        return id;
    }
}
