package com.pvpwow.lfg.application.port.out;

import com.pvpwow.lfg.domain.player.PlayerId;

/**
 * Driven port: who is calling. Stub phase: the dev adapter reads the
 * {@code X-Player-Id} header; Battle.net OAuth later replaces only that
 * adapter, never this port.
 */
public interface CurrentPlayerProvider {

    PlayerId currentPlayer();
}
