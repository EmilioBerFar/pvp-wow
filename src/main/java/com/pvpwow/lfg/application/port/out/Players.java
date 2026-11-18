package com.pvpwow.lfg.application.port.out;

import com.pvpwow.lfg.domain.player.BattleNetId;
import com.pvpwow.lfg.domain.player.Player;
import com.pvpwow.lfg.domain.player.PlayerId;

import java.util.Optional;

/**
 * Driven port: player persistence.
 *
 * <p>Two ways to find one row, because two callers ask two different
 * questions: "who is calling" (by our id) and "has this Battle.net account
 * been here before" (by the account id). Each one is answered by an index;
 * neither of them scans the table.
 *
 * <p>There is no lookup by battle tag on purpose: the tag is mutable and NOT
 * unique (Blizzard reclaims them), so it cannot identify a row. It is contact
 * data the product shows, nothing more.
 */
public interface Players {

    void save(Player player);

    Optional<Player> findById(PlayerId id);

    Optional<Player> findByBattleNetId(BattleNetId battleNetId);
}
