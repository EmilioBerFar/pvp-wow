package com.pvpwow.lfg.adapters.in.rest;

import com.pvpwow.lfg.domain.character.Bracket;
import com.pvpwow.lfg.domain.shared.DomainRuleViolationException;

import java.time.DateTimeException;
import java.time.DayOfWeek;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;
import java.util.Locale;

/**
 * The wire conventions, in one place. Every inbound request applies the same
 * rules: enum names are case-insensitive, brackets use the friendly
 * {@code 2v2}/{@code 3v3} form, and a missing or malformed declared fact is a
 * {@link DomainRuleViolationException} (422) — never a null dereference (500).
 */
final class WireParsing {

    private WireParsing() {
    }

    static String required(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new DomainRuleViolationException("%s is required".formatted(field));
        }
        return value.trim();
    }

    static <E extends Enum<E>> E parseEnum(Class<E> type, String value) {
        String present = required(value, type.getSimpleName());
        try {
            return Enum.valueOf(type, present.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException unknownValue) {
            throw new DomainRuleViolationException(
                    "Unknown %s '%s'".formatted(type.getSimpleName(), value));
        }
    }

    static Bracket bracket(String key) {
        String normalized = required(key, "bracket").toLowerCase(Locale.ROOT);
        return switch (normalized) {
            case "2v2" -> Bracket.TWO_VS_TWO;
            case "3v3" -> Bracket.THREE_VS_THREE;
            default -> throw new DomainRuleViolationException(
                    "Unknown bracket '%s' — valid: 2v2, 3v3".formatted(key));
        };
    }

    static String bracketKey(Bracket bracket) {
        return switch (bracket) {
            case TWO_VS_TWO -> "2v2";
            case THREE_VS_THREE -> "3v3";
        };
    }

    static DayOfWeek day(String value) {
        String present = required(value, "day");
        try {
            return DayOfWeek.valueOf(present.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException unknownDay) {
            throw new DomainRuleViolationException("Unknown day '%s'".formatted(value));
        }
    }

    static LocalTime time(String value, String field) {
        String present = required(value, field);
        try {
            return LocalTime.parse(present);
        } catch (DateTimeParseException malformed) {
            throw new DomainRuleViolationException(
                    "%s must be an ISO time (HH:mm), got: '%s'".formatted(field, value));
        }
    }

    static ZoneId zone(String value) {
        String present = required(value, "zone");
        try {
            return ZoneId.of(present);
        } catch (DateTimeException malformed) {
            throw new DomainRuleViolationException("Unknown zone '%s'".formatted(value));
        }
    }
}
