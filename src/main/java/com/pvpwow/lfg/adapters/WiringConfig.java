package com.pvpwow.lfg.adapters;

import com.pvpwow.lfg.application.port.in.RegisterCharacter;
import com.pvpwow.lfg.application.port.out.Characters;
import com.pvpwow.lfg.application.port.out.CurrentPlayerProvider;
import com.pvpwow.lfg.application.port.out.Seasons;
import com.pvpwow.lfg.application.service.RegisterCharacterService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

/**
 * Composition root of the adapters: the ONLY place where a framework-free
 * application service becomes a Spring bean. It speaks PORTS, not adapter
 * classes — the wiring never names an implementation.
 */
@Configuration
public class WiringConfig {

    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }

    @Bean
    RegisterCharacter registerCharacter(Characters characters,
                                        CurrentPlayerProvider currentPlayer,
                                        Seasons seasons,
                                        Clock clock) {
        return new RegisterCharacterService(characters, currentPlayer, seasons, clock);
    }
}
