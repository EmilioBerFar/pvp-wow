package com.pvpwow.lfg.adapter.armory;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Objects;

/**
 * Builds a link to the Blizzard armory web page for a character.
 *
 * <p>Deliberately NOT in the domain. Three reasons, each one a real defect the
 * domain version had:
 *
 * <ul>
 *   <li>The locale is presentation. An earlier version derived eu → {@code
 *       es-es} for every EU player, which serves Spanish pages to a French
 *       player. The caller decides the locale; the domain never could.
 *   <li>The realm slug must be the CANONICAL one. Deriving it by replacing
 *       spaces with hyphens was a guess that breaks on localized realm names
 *       (the same EU realm is "Sanguino" or "Bloodhoof" depending on the
 *       client). The slug comes from Blizzard's profile API, never from
 *       user input.
 *   <li>Path segments must be percent-encoded. A name like {@code Ñandú}
 *       produced an invalid URL from the domain version.
 * </ul>
 *
 * <p>This is an edge concern: a web layer calls it at render time, and nothing
 * is stored.
 */
public final class ArmoryLinks {

    private static final String BASE = "https://worldofwarcraft.blizzard.com";

    private ArmoryLinks() {
    }

    /**
     * @param locale      page locale, e.g. {@code es-es}; the caller's choice
     * @param realmSlug   canonical realm slug from the Blizzard profile API
     * @param characterName character name as shown in game (case preserved)
     */
    public static String url(String locale, String realmSlug, String characterName) {
        Objects.requireNonNull(locale, "locale must not be null");
        Objects.requireNonNull(realmSlug, "realmSlug must not be null");
        Objects.requireNonNull(characterName, "characterName must not be null");
        if (locale.isBlank() || realmSlug.isBlank() || characterName.isBlank()) {
            throw new IllegalArgumentException(
                    "locale, realmSlug and characterName must not be blank");
        }
        return "%s/%s/character/%s/%s".formatted(BASE, encode(locale),
                encode(realmSlug), encode(characterName));
    }

    /**
     * Percent-encodes one path segment. {@link URLEncoder} is form-encoding, so
     * its {@code +} for spaces is rewritten to {@code %20} — a path segment
     * with a literal {@code +} would mean something else.
     */
    private static String encode(String segment) {
        return URLEncoder.encode(segment, StandardCharsets.UTF_8).replace("+", "%20");
    }
}
