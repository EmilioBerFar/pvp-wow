package com.pvpwow.lfg.adapter.armory;

import com.pvpwow.lfg.domain.character.Region;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Objects;

/**
 * Builds a link to the Blizzard armory web page for a character.
 *
 * <p>Deliberately NOT in the domain. The reasons, each one a real defect the
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
 *   <li>The URL's shape is Blizzard's web contract and it is NOT stable. The
 *       first version of this class built {@code /{locale}/character/{slug}/
 *       {name}} — a shape that no longer exists. Verified against a live
 *       character (2026-11-01) and corroborated by other live URLs, the
 *       current shape is {@code /{locale}/{game-version}/{region}/armory/
 *       character/{slug}/{name}}, with the name lowercase even though
 *       in-game names are capitalized.
 * </ul>
 *
 * <p>This is an edge concern: a web layer calls it at render time, and nothing
 * is stored.
 */
public final class ArmoryLinks {

    private static final String BASE = "https://worldofwarcraft.blizzard.com";

    /**
     * Game-version path segment of the CURRENT armory. This is a fact about
     * Blizzard's website, not a domain concept: retail is {@code worldsoul}
     * (The War Within), Classic is {@code classic}. When the next expansion
     * replaces it, this constant and the pinning test are all that changes.
     */
    private static final String GAME_VERSION = "worldsoul";

    private ArmoryLinks() {
    }

    /**
     * @param region        the character's region — part of the path, lowercased
     * @param locale        page locale, e.g. {@code es-es}; the caller's choice
     * @param realmSlug     canonical realm slug from the Blizzard profile API
     * @param characterName character name as shown in game
     */
    public static String url(Region region, String locale, String realmSlug, String characterName) {
        Objects.requireNonNull(region, "region must not be null");
        Objects.requireNonNull(locale, "locale must not be null");
        Objects.requireNonNull(realmSlug, "realmSlug must not be null");
        Objects.requireNonNull(characterName, "characterName must not be null");
        if (locale.isBlank() || realmSlug.isBlank() || characterName.isBlank()) {
            throw new IllegalArgumentException(
                    "locale, realmSlug and characterName must not be blank");
        }
        return "%s/%s/%s/%s/armory/character/%s/%s".formatted(
                BASE,
                encode(locale.toLowerCase(Locale.ROOT)),
                GAME_VERSION,
                region.name().toLowerCase(Locale.ROOT),
                encode(realmSlug.toLowerCase(Locale.ROOT)),
                encode(characterName.toLowerCase(Locale.ROOT)));
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
