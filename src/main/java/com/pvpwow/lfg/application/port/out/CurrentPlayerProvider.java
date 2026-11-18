package com.pvpwow.lfg.application.port.out;

import com.pvpwow.lfg.domain.player.PlayerId;

/**
 * Driven port: who is calling. The only adapter reads the session the
 * Battle.net login left behind.
 */
public interface CurrentPlayerProvider {

    PlayerId currentPlayer();
}
