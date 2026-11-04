package com.pvpwow.lfg.application.port.out;

import com.pvpwow.lfg.domain.player.BattleTag;
import com.pvpwow.lfg.domain.player.Player;
import com.pvpwow.lfg.domain.player.PlayerId;
import com.pvpwow.lfg.domain.player.Username;

import java.util.Optional;

/**
 * Driven port: player persistence.
 *
 * <p>The two "taken" questions exist for the SAME reason the character
 * duplicate check exists: the database index is the law, and this is the
 * friendly answer the caller gets before hitting it. A concurrent registration
 * can still race past both checks; the index, not the service, is what keeps
 * the data correct.
 */
public interface Players {

    void save(Player player);

    Optional<Player> findById(PlayerId id);

    /**
     * Case-insensitive, because a login handle is not a display name: two
     * players asking for {@code Emilio} and {@code emilio} want the same
     * handle, and only one can have it.
     */
    boolean usernameTaken(Username username);

    /**
     * Battle tags are already case-folded by the value object, so this is a
     * plain equality check. One Battle.net account is one player.
     */
    boolean battleTagTaken(BattleTag battleTag);
}
