package com.pvpwow.lfg.adapters.in.rest;

import com.pvpwow.lfg.application.port.in.RegisterCharacter.RegisterCharacterCommand;
import com.pvpwow.lfg.domain.character.Bracket;
import com.pvpwow.lfg.domain.character.CharacterName;
import com.pvpwow.lfg.domain.character.Realm;
import com.pvpwow.lfg.domain.character.Region;
import com.pvpwow.lfg.domain.character.Specialization;
import com.pvpwow.lfg.domain.character.WowClass;
import com.pvpwow.lfg.domain.shared.DomainRuleViolationException;

import java.util.Locale;
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
 * <p>Wire conventions: bracket keys are friendly ({@code 2v2}, {@code 3v3});
 * everything else uses enum names ({@code EU}, {@code MAGE},
 * {@code MAGE_FROST}), case-insensitive.
 */
record RegisterCharacterRequest(
        String name,
        String realm,
        String region,
        String wowClass,
        Set<String> specs,
        Integer itemLevel,
        Map<String, Integer> ratings) {

    RegisterCharacterCommand toCommand() {
        return new RegisterCharacterCommand(
                new CharacterName(required(name, "name")),
                new Realm(required(realm, "realm")),
                parse(Region.class, required(region, "region")),
                parse(WowClass.class, required(wowClass, "wowClass")),
                specs == null ? Set.of()
                        : specs.stream()
                                .map(spec -> parse(Specialization.class, required(spec, "spec")))
                                .collect(Collectors.toUnmodifiableSet()),
                itemLevel == null ? reject("itemLevel is required") : itemLevel,
                ratings == null ? Map.of()
                        : ratings.entrySet().stream()
                                .collect(Collectors.toUnmodifiableMap(
                                        entry -> bracketOf(entry.getKey()),
                                        entry -> ratingOf(entry.getValue()))));
    }

    private static String required(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new DomainRuleViolationException("%s is required".formatted(field));
        }
        return value.trim();
    }

    private static int ratingOf(Integer rating) {
        if (rating == null) {
            throw new DomainRuleViolationException("A rating must be a number, not null");
        }
        return rating;
    }

    private static Bracket bracketOf(String key) {
        String normalized = required(key, "bracket").toLowerCase(Locale.ROOT);
        return switch (normalized) {
            case "2v2" -> Bracket.TWO_VS_TWO;
            case "3v3" -> Bracket.THREE_VS_THREE;
            default -> throw new DomainRuleViolationException(
                    "Unknown bracket '%s' — valid: 2v2, 3v3".formatted(key));
        };
    }

    private static <E extends Enum<E>> E parse(Class<E> type, String value) {
        try {
            return Enum.valueOf(type, value.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException unknownValue) {
            throw new DomainRuleViolationException(
                    "Unknown %s '%s'".formatted(type.getSimpleName(), value));
        }
    }

    private static <T> T reject(String message) {
        throw new DomainRuleViolationException(message);
    }
}
