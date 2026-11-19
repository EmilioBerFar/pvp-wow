package com.pvpwow.lfg.adapters.out.blizzard;

import com.pvpwow.lfg.application.port.out.BattleNetCharacters;
import com.pvpwow.lfg.application.port.out.BattleNetCharacters.AccountCharacter;
import com.pvpwow.lfg.application.port.out.BattleNetCharacters.CharacterFacts;
import com.pvpwow.lfg.application.port.out.BattleNetCharacters.CharacterReference;
import com.pvpwow.lfg.domain.character.Bracket;
import com.pvpwow.lfg.domain.character.PvpSeason;
import com.pvpwow.lfg.domain.character.Region;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * The Blizzard profile API, as a witness.
 *
 * <p>Three endpoints, and the cost is the reason they are three:
 *
 * <ul>
 *   <li>{@code GET /profile/user/wow} - the characters of the account. ONE
 *       call, and it is enough to show the player what they could import.</li>
 *   <li>{@code GET /profile/wow/character/{slug}/{name}} - the class, the
 *       equipped item level and the active specialization.</li>
 *   <li>{@code GET /profile/wow/character/{slug}/{name}/pvp-summary} - WHICH
 *       brackets the character has played. The summary carries only LINKS to
 *       the brackets, never the ratings, so it cannot answer the rating
 *       question by itself.</li>
 *   <li>{@code GET /profile/wow/character/{slug}/{name}/pvp-bracket/{bracket}} -
 *       the rating of one bracket, with the season it belongs to.</li>
 * </ul>
 *
 * <p>WHY THE SUMMARY IS ASKED BEFORE THE BRACKETS: a bracket the character
 * has never played answers 404. Asking for it anyway spends a call to learn
 * something the summary already said. The summary says which brackets exist,
 * and only those are asked for. A character with no arena play costs two
 * calls; one with both brackets costs four.
 *
 * <p>THE 404 IS NOT AN ERROR, IT IS "UNRANKED": a bracket the character has
 * not played is not a failure, it is the absence of a standing. Our domain
 * says the same thing with an absent {@code PvpStanding}, so the two
 * vocabularies line up exactly. What IS an error is a profile that cannot
 * be read at all, and that is reported as an empty answer rather than as an
 * exception, because "there is nothing there" and "something went wrong"
 * are different things and the caller has to be able to tell them apart.
 */
@Component
final class BlizzardProfileClient implements BattleNetCharacters {

    private static final Logger log = LoggerFactory.getLogger(BlizzardProfileClient.class);

    private static final String NAMESPACE = "profile";
    private static final String LOCALE = "en_US";

    /**
     * The brackets this product knows about. The profile API exposes 2v2 and
     * 3v3 (and, off retail, RBG and per-spec shuffle); our domain has exactly
     * the first two, and a bracket we cannot store is a bracket we do not
     * ask for.
     */
    private static final List<Bracket> BRACKETS = List.of(Bracket.TWO_VS_TWO, Bracket.THREE_VS_THREE);

    private final RestClient blizzard;
    private final BattleNetAccessToken accessToken;
    private final String baseUrlOverride;

    BlizzardProfileClient(RestClient.Builder restClientBuilder,
                          BattleNetAccessToken accessToken,
                          @Value("${pvp.blizzard.base-url:}") String baseUrlOverride) {
        this.blizzard = restClientBuilder.build();
        this.accessToken = accessToken;
        this.baseUrlOverride = baseUrlOverride == null ? "" : baseUrlOverride.trim();
    }

    @Override
    public List<AccountCharacter> charactersIn(Region region) {
        AccountProfile profile = get("/profile/user/wow", region, AccountProfile.class);
        List<AccountCharacter> characters = new ArrayList<>();
        if (profile == null || profile.wowAccounts() == null) {
            return characters;
        }
        for (WowAccount account : profile.wowAccounts()) {
            if (account == null || account.characters() == null) {
                continue;
            }
            for (AccountCharacterSummary character : account.characters()) {
                if (character == null || character.name() == null
                        || character.realm() == null
                        || character.playableClass() == null) {
                    // A character the upstream reports without the fields we
                    // need is not a character we can show or import. Skipping
                    // it is honest: we do not know who it is.
                    continue;
                }
                characters.add(new AccountCharacter(
                        character.id() == null ? 0L : character.id(),
                        character.name(),
                        character.realm().slug(),
                        character.playableClass().name(),
                        character.level() == null ? 0 : character.level()));
            }
        }
        return characters;
    }

    @Override
    public Optional<CharacterFacts> factsOf(CharacterReference character) {
        // THE SEGMENTS ARE ENCODED. The realm slug and the character name
        // become PATH SEGMENTS, and a segment that carries a slash, a space
        // or a '#' would change the path instead of naming a character. The
        // slug of a realm like "Festung der Stürme" carries an accent, and
        // a name could carry one too.
        //
        // The encoding is applied to the SEGMENT, not to the whole path:
        // encoding the whole thing would escape the slashes that separate
        // the segments, and the upstream would receive one long segment.
        String path = "/profile/wow/character/" + encodeSegment(character.realmSlug()) + "/"
                + encodeSegment(character.name().toLowerCase(Locale.ROOT));

        CharacterProfile profile = get(path, character.region(), CharacterProfile.class);
        if (profile == null || profile.characterClass() == null
                || profile.characterClass().name() == null) {
            return Optional.empty();
        }
        if (profile.equippedItemLevel() == null) {
            // Gear is not optional in our domain, and the upstream did not
            // report any. There is no gear to observe.
            return Optional.empty();
        }

        Map<Bracket, BattleNetCharacters.Rating> ratings = ratingsOf(path, character.region());
        return Optional.of(new CharacterFacts(
                profile.characterClass().name(),
                profile.activeSpec() == null ? null : profile.activeSpec().name(),
                profile.equippedItemLevel(),
                ratings));
    }

    /**
     * The ratings of the brackets the character has actually played.
     */
    private Map<Bracket, BattleNetCharacters.Rating> ratingsOf(String characterPath, Region region) {
        Map<Bracket, BattleNetCharacters.Rating> ratings = new LinkedHashMap<>();

        PvpSummary summary = get(characterPath + "/pvp-summary", region, PvpSummary.class);
        for (Bracket bracket : BRACKETS) {
            if (summary != null && !summary.plays(bracket)) {
                // The summary says this bracket does not exist for this
                // character. Asking would spend a call to learn a 404.
                continue;
            }
            PvpBracketStatistics statistics = get(characterPath + "/pvp-bracket/" + bracketKey(bracket),
                    region, PvpBracketStatistics.class);
            if (statistics == null) {
                // A 404 here means the character has not played this bracket:
                // unranked, not broken. The domain says the same thing with an
                // absent standing.
                continue;
            }
            ratings.put(bracket, new BattleNetCharacters.Rating(
                    statistics.rating(),
                    seasonOf(statistics.season())));
        }
        return ratings;
    }

    /**
     * A GET that answers with the body, or with nothing when the upstream
     * says the thing is not there.
     */
    private <T> T get(String path, Region region, Class<T> type) {
        try {
            return blizzard.get()
                    .uri(uriFor(path, region))
                    .header("Authorization", "Bearer " + accessToken.accessTokenOfCurrentPlayer())
                    .retrieve()
                    .body(type);
        } catch (RestClientException notThere) {
            // 404 is the upstream's way of saying "this character or bracket
            // does not exist". It is an answer, not a failure.
            log.debug("Blizzard answered 404 for {}: {}", path, notThere.getMessage());
            return null;
        }
    }

    /**
     * One path segment, encoded.
     *
     * <p>{@link java.net.URLEncoder} is for FORM bodies, not for paths: it
     * turns a space into {@code +}, which in a path means a literal plus.
     * So the space becomes {@code %20} and everything else keeps the
     * percent-encoding - including a slash, which MUST stay escaped or it
     * would split the segment and change the path.
     */
    private static String encodeSegment(String segment) {
        return java.net.URLEncoder.encode(segment, java.nio.charset.StandardCharsets.UTF_8)
                .replace("+", "%20");
    }

    /**
     * The URL of one call.
     *
     * <p>THE OVERRIDE IS A BASE URL, host and port together. It has to be:
     * {@code UriBuilder.host()} escapes the colon of "localhost:8080" and
     * produces a URI nothing can fetch - and the tests need to point at a
     * stub, because a test run has no business calling a third party.
     */
    private java.net.URI uriFor(String path, Region region) {
        String base = baseUrlOverride.isEmpty()
                ? "https://" + hostOf(region)
                : baseUrlOverride;
        return java.net.URI.create(base + path
                + "?namespace=" + NAMESPACE + "-" + apiSuffixOf(region)
                + "&locale=" + LOCALE);
    }

    /**
     * The host that serves a region.
     *
     * <p>It lives HERE and not in the domain's {@code Region}: the domain
     * knows that rated PvP does not cross regions, and it has no idea that
     * Blizzard serves them from {@code {region}.api.blizzard.com}. A domain
     * enum that knew the upstream's hostnames would know about the upstream.
     *
     * <p>THE OVERRIDE EXISTS FOR THE TESTS, and for one honest reason: a
     * test run has no business calling a third party. The alternative would
     * be a test that reaches the real Blizzard over the network - slow,
     * flaky, and dependent on somebody's account. With the override, the
     * test points at its own stub and the production default stays exactly
     * what Blizzard serves.
     */
    private String hostOf(Region region) {
        return switch (region) {
            case EU -> "eu.api.blizzard.com";
            case US -> "us.api.blizzard.com";
            case KR -> "kr.api.blizzard.com";
            case TW -> "tw.api.blizzard.com";
            case CN -> "cn.api.blizzard.com";
        };
    }

    /**
     * The namespace suffix of a region: {@code profile-eu}, {@code profile-us}.
     */
    private static String apiSuffixOf(Region region) {
        return switch (region) {
            case EU -> "eu";
            case US -> "us";
            case KR -> "kr";
            case TW -> "tw";
            case CN -> "cn";
        };
    }

    /**
     * The season id, as the domain models it.
     *
     * <p>Blizzard reports season ids as JSON numbers that fit in a long; the
     * domain models them as an int. The conversion is checked rather than
     * cast: a season id that does not fit is not a season id this product can
     * store, and truncating it would file a rating under the wrong season -
     * which is exactly the mistake the season id exists to prevent.
     */
    private static PvpSeason seasonOf(SeasonSummary season) {
        long id = season.id();
        if (id <= 0 || id > Integer.MAX_VALUE) {
            throw new IllegalStateException(
                    "Blizzard reported a season id this product cannot store: " + id);
        }
        return new PvpSeason((int) id);
    }

    private static String bracketKey(Bracket bracket) {
        return switch (bracket) {
            case TWO_VS_TWO -> "2v2";
            case THREE_VS_THREE -> "3v3";
        };
    }

    // ---- the upstream's shapes, as they arrive ----
    //
    // The property names are spelled out with @JsonProperty instead of
    // setting a global naming strategy: Blizzard's JSON is snake_case and
    // ours is camelCase, but a global strategy would change the JSON this
    // application PRODUCES as well - and the product's own wire format is
    // not a thing a third party's conventions get to decide.

    record AccountProfile(@JsonProperty("wow_accounts") List<WowAccount> wowAccounts) {
    }

    record WowAccount(long id, List<AccountCharacterSummary> characters) {
    }

    /**
     * One character as the account listing reports it.
     *
     * <p>The upstream omits fields it has no value for - a realm without a
     * name, a class without an id - so the ids are boxed: a primitive long
     * would fail the WHOLE mapping when the field is absent, and a missing
     * id is not a reason to lose the character's name.
     */
    record AccountCharacterSummary(
            Long id,
            String name,
            RealmSummary realm,
            @JsonProperty("playable_class") NamedId playableClass,
            Integer level) {
    }

    record RealmSummary(Long id, String name, String slug) {
    }

    record NamedId(Long id, String name) {
    }

    /**
     * One character's own profile.
     *
     * <p>{@code active_spec} is ABSENT for characters below level 10 - the
     * upstream omits it because there is no specialization yet. It is boxed
     * for the same reason: an absent active spec must not cost us the gear
     * and the class that ARE there.
     */
    record CharacterProfile(
            @JsonProperty("character_class") NamedId characterClass,
            @JsonProperty("active_spec") NamedId activeSpec,
            @JsonProperty("average_item_level") Integer averageItemLevel,
            @JsonProperty("equipped_item_level") Integer equippedItemLevel,
            @JsonProperty("pvp_summary") PvpSummaryLink pvpSummary) {
    }

    record PvpSummaryLink(String href) {
    }

    /**
     * The PvP summary: WHICH brackets the character has played.
     *
     * <p>The upstream sends an array of OBJECTS ({@code {"href": ...}}), not
     * an array of strings, so the links are read from a small record. The
     * summary carries no ratings at all - that is why the brackets are
     * fetched one by one afterwards.
     */
    record PvpSummary(List<PvpBracketLink> brackets, Integer honorLevel) {

        /**
         * Whether the summary says this character has played this bracket.
         *
         * <p>The summary lists only the brackets the character HAS rated, so
         * a bracket missing from the list means "unranked". An empty list
         * means the character has no arena record at all.
         */
        boolean plays(Bracket bracket) {
            if (brackets == null) {
                return false;
            }
            return brackets.stream()
                    .anyMatch(link -> link != null && link.href() != null
                            && link.href().contains("/" + bracketKey(bracket)));
        }
    }

    record PvpBracketLink(String href) {
    }

    record PvpBracketStatistics(int rating, SeasonSummary season) {
    }

    record SeasonSummary(long id) {
    }
}
