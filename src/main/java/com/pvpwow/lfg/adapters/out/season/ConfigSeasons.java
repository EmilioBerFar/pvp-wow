package com.pvpwow.lfg.adapters.out.season;

import com.pvpwow.lfg.application.port.out.Seasons;
import com.pvpwow.lfg.domain.character.PvpSeason;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * The active season as operational config: one property, updated each
 * season. An honest bootstrap — later the Blizzard sync owns this fact.
 */
@Component
final class ConfigSeasons implements Seasons {

    private final int currentSeasonId;

    ConfigSeasons(@Value("${pvp.current-season}") int currentSeasonId) {
        this.currentSeasonId = currentSeasonId;
    }

    @Override
    public PvpSeason current() {
        return new PvpSeason(currentSeasonId);
    }
}
