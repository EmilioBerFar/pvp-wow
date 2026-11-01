package com.pvpwow.lfg.application.service;

import com.pvpwow.lfg.application.port.in.RegisterCharacter;
import com.pvpwow.lfg.application.port.in.RegisterCharacter.RegisterCharacterCommand;
import com.pvpwow.lfg.application.port.out.Characters;
import com.pvpwow.lfg.application.port.out.CurrentPlayerProvider;
import com.pvpwow.lfg.application.port.out.Seasons;
import com.pvpwow.lfg.domain.character.Bracket;
import com.pvpwow.lfg.domain.character.Character;
import com.pvpwow.lfg.domain.character.CharacterId;
import com.pvpwow.lfg.domain.character.GearSnapshot;
import com.pvpwow.lfg.domain.character.ItemLevel;
import com.pvpwow.lfg.domain.character.PvpStanding;
import com.pvpwow.lfg.domain.character.PvpSeason;
import com.pvpwow.lfg.domain.character.Rating;
import com.pvpwow.lfg.domain.shared.DomainRuleViolationException;

import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * RegisterCharacter, orchestrated: resolve the caller, stamp the observed
 * facts (this service IS the witness of a self-report), build the aggregate
 * and hand it to persistence.
 *
 * <p>Validation of DECLARED facts (name, realm, class, specs) already happened
 * by construction in the command — value objects cannot exist in an invalid
 * state. Validation of OBSERVED facts (item level, ratings) happens here, at
 * the exact moment the stamps are applied.
 */
public final class RegisterCharacterService implements RegisterCharacter {

    private final Characters characters;
    private final CurrentPlayerProvider currentPlayer;
    private final Seasons seasons;
    private final Clock clock;

    public RegisterCharacterService(Characters characters, CurrentPlayerProvider currentPlayer,
                                    Seasons seasons, Clock clock) {
        this.characters = Objects.requireNonNull(characters, "characters must not be null");
        this.currentPlayer = Objects.requireNonNull(currentPlayer, "currentPlayer must not be null");
        this.seasons = Objects.requireNonNull(seasons, "seasons must not be null");
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
    }

    @Override
    public CharacterId register(RegisterCharacterCommand command) {
        if (characters.alreadyRegistered(command.region(), command.realm(), command.name())) {
            throw new DomainRuleViolationException(
                    "Character %s on realm %s (%s) is already registered".formatted(
                            command.name().value(), command.realm().value(), command.region()));
        }
        Instant now = clock.instant();
        GearSnapshot gear = new GearSnapshot(new ItemLevel(command.itemLevel()), now);
        Map<Bracket, PvpStanding> standings =
                stampStandings(command.ratings(), seasons.current(), now);
        Character character = Character.register(currentPlayer.currentPlayer(), command.name(),
                command.realm(), command.region(), command.wowClass(), gear, command.specs(),
                standings);
        characters.save(character);
        return character.id();
    }

    private Map<Bracket, PvpStanding> stampStandings(Map<Bracket, Integer> ratings,
                                                     PvpSeason season, Instant now) {
        return ratings.entrySet().stream()
                .collect(Collectors.toUnmodifiableMap(Map.Entry::getKey,
                        entry -> new PvpStanding(new Rating(entry.getValue()), season, now)));
    }
}
