package com.pvpwow.lfg.adapters.out.persistence;

import com.pvpwow.lfg.application.port.out.Players;
import com.pvpwow.lfg.domain.player.BattleTag;
import com.pvpwow.lfg.domain.player.Player;
import com.pvpwow.lfg.domain.player.PlayerId;
import com.pvpwow.lfg.domain.player.Username;

/**
 * WU17 gave characters.owner_id a real foreign key, so an IT that inserts a
 * character must insert its owner first. This helper derives a valid handle
 * and battle tag from the id itself: the tests care about the OWNER RELATION,
 * not about inventing believable identity, and a deterministic derivation
 * cannot collide with a hand-written fixture.
 */
final class PlayersFixture {

    private PlayersFixture() {
    }

    static PlayerId ensure(Players players, PlayerId id) {
        if (players.findById(id).isEmpty()) {
            String handle = "u" + id.value().toString().replace("-", "").substring(0, 8);
            players.save(Player.rehydrate(id,
                    new Username(handle),
                    new BattleTag(handle + "#1234")));
        }
        return id;
    }
}
