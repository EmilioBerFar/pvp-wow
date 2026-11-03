package com.pvpwow.lfg.adapters.in.rest;

import com.pvpwow.lfg.application.port.in.PublishListing.PublishListingCommand;
import com.pvpwow.lfg.domain.character.CharacterId;
import com.pvpwow.lfg.domain.character.Specialization;
import com.pvpwow.lfg.domain.listing.AvailabilitySchedule;
import com.pvpwow.lfg.domain.listing.Language;
import com.pvpwow.lfg.domain.listing.TimeWindow;
import com.pvpwow.lfg.domain.shared.DomainRuleViolationException;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Wire representation of a publication request.
 *
 * <p>No TTL field exists, and that is the contract: expiration is a server
 * policy. The request carries only declared facts, which become domain objects
 * here so invalid input dies at the edge with a 422.
 *
 * <p>PRESENCE is declared with Bean Validation ({@code @NotNull},
 * {@code @NotBlank}, {@code @NotEmpty}, {@code @Valid} in cascade, and
 * {@code List<@NotNull @Valid ...>} for list elements). The validation
 * framework rejects the missing field BEFORE {@link #toCommand()} runs, so no
 * absent value can ever reach a command's null guard. Format and business
 * rules stay in {@link WireParsing} and in the domain value objects.
 *
 * <p>Every field here is required: a listing without a schedule, without a
 * zone, without windows or without a language is not a listing.
 */
record PublishListingRequest(
        @NotNull @NotBlank String characterId,
        @NotNull @NotBlank String bracket,
        @NotNull @NotBlank String spec,
        @NotEmpty Set<@NotNull String> languages,
        @NotNull @Valid ScheduleRequest schedule) {

    PublishListingCommand toCommand() {
        return new PublishListingCommand(
                new CharacterId(uuid(WireParsing.required(characterId, "characterId"))),
                WireParsing.bracket(bracket),
                WireParsing.parseEnum(Specialization.class, spec),
                languages.stream()
                        .map(language -> WireParsing.parseEnum(Language.class, language))
                        .collect(Collectors.toUnmodifiableSet()),
                schedule.toDomain());
    }

    private static UUID uuid(String value) {
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException malformed) {
            throw new DomainRuleViolationException(
                    "characterId must be a UUID, got: '%s'".formatted(value));
        }
    }

    record ScheduleRequest(
            @NotNull @NotBlank String zone,
            @NotEmpty List<@NotNull @Valid WindowRequest> windows) {

        AvailabilitySchedule toDomain() {
            // Collectors.toSet(), not toUnmodifiableSet(): duplicate windows are
            // collapsed by the Set type, never an IllegalArgumentException (500).
            Set<TimeWindow> parsed = windows.stream()
                    .map(WindowRequest::toDomain)
                    .collect(Collectors.toSet());
            return new AvailabilitySchedule(parsed, WireParsing.zone(zone));
        }
    }

    record WindowRequest(
            @NotNull @NotBlank String day,
            @NotNull @NotBlank String start,
            @NotNull @NotBlank String end) {

        TimeWindow toDomain() {
            return new TimeWindow(
                    WireParsing.day(day),
                    WireParsing.time(start, "start"),
                    WireParsing.time(end, "end"));
        }
    }
}
