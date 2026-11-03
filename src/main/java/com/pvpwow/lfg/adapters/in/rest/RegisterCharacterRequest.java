package com.pvpwow.lfg.adapters.in.rest;

import com.pvpwow.lfg.application.port.in.RegisterCharacter.RegisterCharacterCommand;
import com.pvpwow.lfg.domain.character.CharacterName;
import com.pvpwow.lfg.domain.character.Realm;
import com.pvpwow.lfg.domain.character.Region;
import com.pvpwow.lfg.domain.character.Specialization;
import com.pvpwow.lfg.domain.character.WowClass;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Wire representation of a registration request.
 *
 * <p>This is where command option C pays its adapter cost: every declared
 * string becomes a value object HERE, and an invalid one dies HERE — exactly
 * where the advice lives to translate it into a 422. The use case never sees
 * an invalid declared fact.
 *
 * <p>PRESENCE is declared with Bean Validation, so a field added to this record
 * without {@code @NotNull} fails the test suite rather than production.
 * Format and business rules stay in {@link WireParsing} and in the domain
 * value objects.
 *
 * <p>{@code ratings} is the one OPTIONAL field, and on purpose: a character
 * may be registered unranked, so an absent map is a valid request. Its
 * CONTENTS are constrained when present, and the domain decides what an
 * absent map means (every bracket stays unranked).
 */
record RegisterCharacterRequest(
        @NotNull @NotBlank String name,
        @NotNull @NotBlank String realm,
        @NotNull @NotBlank String region,
        @NotNull @NotBlank String wowClass,
        @NotEmpty Set<@NotNull @NotBlank String> specs,
        @NotNull @Min(1) Integer itemLevel,
        @Valid Map<@NotNull @NotBlank String, @NotNull @Min(0) Integer> ratings) {

    // `ratings` is OPTIONAL: an unranked character is a valid registration,
    // not a malformed request. Only its CONTENTS are validated when present.
    // Absent means "every bracket stays unranked".

    RegisterCharacterCommand toCommand() {
        return new RegisterCharacterCommand(
                new CharacterName(WireParsing.required(name, "name")),
                new Realm(WireParsing.required(realm, "realm")),
                WireParsing.parseEnum(Region.class, WireParsing.required(region, "region")),
                WireParsing.parseEnum(WowClass.class, WireParsing.required(wowClass, "wowClass")),
                specs.stream()
                        .map(spec -> WireParsing.parseEnum(Specialization.class,
                                WireParsing.required(spec, "spec")))
                        .collect(Collectors.toUnmodifiableSet()),
                itemLevel,
                ratings == null ? Map.of()
                        : ratings.entrySet().stream()
                                .collect(Collectors.toUnmodifiableMap(
                                        entry -> WireParsing.bracket(entry.getKey()),
                                        entry -> entry.getValue())));
    }
}
