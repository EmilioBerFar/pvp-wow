package com.pvpwow.lfg.application.port.out;

import com.pvpwow.lfg.domain.character.PvpSeason;

/**
 * Driven port: the currently active PvP season. A rating means nothing
 * without its season, so the application must know it when stamping a
 * self-reported standing. Bootstrap: a config-backed adapter (one property,
 * updated each season); later the Blizzard sync keeps it honest.
 */
public interface Seasons {

    PvpSeason current();
}
