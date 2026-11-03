package com.pvpwow.lfg.adapters.in.rest;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Path;

import java.util.Collection;
import java.util.stream.Collectors;

/**
 * Turns Bean Validation failures into our vocabulary: a 422 with the same
 * "field is required" wording the hand-written guards used to produce.
 *
 * <p>Presence is declared, not remembered. A DTO that grows a field without
 * {@code @NotNull} fails the test suite instead of failing in production, and
 * that is the whole point: the input is hostile by default.
 *
 * <p>Only PRESENCE lives in the annotations. Format and business rules stay
 * where they were: {@link WireParsing} parses, the domain value objects
 * validate. Two mechanisms for two different jobs, never three for one.
 */
final class WirePresence {

    private WirePresence() {
    }

    /**
     * The human-readable reason, in the same words the DTO used to throw:
     * {@code characterId is required}. The path keeps the nested structure, so
     * a window's day reads {@code schedule.windows[0].day}.
     */
    static String describe(ConstraintViolation<?> violation) {
        return "%s is required".formatted(fieldOf(violation));
    }

    static String describeAll(Collection<? extends ConstraintViolation<?>> violations) {
        return violations.stream()
                .map(WirePresence::describe)
                .distinct()
                .collect(Collectors.joining(", "));
    }

    /**
     * The violated path without the {@code request} prefix Spring adds, and
     * without the record component's leading dot. Indexed list elements keep
     * their index: {@code schedule.windows[0].day}.
     */
    private static String fieldOf(ConstraintViolation<?> violation) {
        StringBuilder path = new StringBuilder();
        for (Path.Node node : violation.getPropertyPath()) {
            if ("request".equals(node.getName())) {
                continue;
            }
            if (node.getIndex() != null) {
                path.append('[').append(node.getIndex()).append(']');
                continue;
            }
            String name = node.getName();
            if (name.startsWith(".")) {
                name = name.substring(1);
            }
            if (!path.isEmpty()) {
                path.append('.');
            }
            path.append(name);
        }
        return path.isEmpty() ? "request" : path.toString();
    }

    /**
     * A constraint violation that reaches the edge outside a {@code @Valid}
     * body (a path variable, a request parameter) arrives wrapped in this
     * exception.
     */
    static String describe(ConstraintViolationException failure) {
        return describeAll(failure.getConstraintViolations());
    }
}
