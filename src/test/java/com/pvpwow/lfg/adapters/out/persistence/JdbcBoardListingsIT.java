package com.pvpwow.lfg.adapters.out.persistence;

import com.pvpwow.lfg.application.port.out.BoardListings;
import com.pvpwow.lfg.application.port.out.BoardListings.BoardCandidate;
import com.pvpwow.lfg.application.port.out.BoardListings.SearchCriteria;
import com.pvpwow.lfg.application.port.out.Characters;
import com.pvpwow.lfg.application.port.out.Listings;
import com.pvpwow.lfg.domain.character.Bracket;
import com.pvpwow.lfg.domain.character.Character;
import com.pvpwow.lfg.domain.character.CharacterName;
import com.pvpwow.lfg.domain.character.GearSnapshot;
import com.pvpwow.lfg.domain.character.ItemLevel;
import com.pvpwow.lfg.domain.character.PvpSeason;
import com.pvpwow.lfg.domain.character.PvpStanding;
import com.pvpwow.lfg.domain.character.Rating;
import com.pvpwow.lfg.domain.character.Realm;
import com.pvpwow.lfg.domain.character.Region;
import com.pvpwow.lfg.domain.character.Specialization;
import com.pvpwow.lfg.domain.character.WowClass;
import com.pvpwow.lfg.domain.listing.AvailabilitySchedule;
import com.pvpwow.lfg.domain.listing.Language;
import com.pvpwow.lfg.domain.listing.LfgListing;
import com.pvpwow.lfg.domain.listing.TimeWindow;
import com.pvpwow.lfg.domain.player.PlayerId;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

import java.time.DayOfWeek;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The board query against the REAL database. Every rule here is SQL behavior
 * that an in-memory fake cannot prove: optional predicates, the current-season
 * freshness {@code EXISTS}, recency ordering, and batched aggregate hydration.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@Testcontainers
class JdbcBoardListingsIT {

    @Container
    static PostgreSQLContainer postgres =
            new PostgreSQLContainer(DockerImageName.parse("postgres:18-alpine"));

    @DynamicPropertySource
    static void pointTheApplicationAtTheContainer(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    private static final Instant NOW = Instant.parse("2026-11-02T10:00:00Z");
    private static final Instant GEAR_OBSERVED_AT = NOW.minus(Duration.ofDays(2));
    private static final Instant FRESH_AFTER = NOW.minus(Duration.ofDays(30));
    private static final PvpSeason CURRENT_SEASON = new PvpSeason(42);
    private static final Duration TTL = Duration.ofHours(24);
    private static final PlayerId PLAYER = new PlayerId(UUID.randomUUID());
    private static final AvailabilitySchedule SCHEDULE = new AvailabilitySchedule(
            Set.of(new TimeWindow(DayOfWeek.MONDAY, LocalTime.of(18, 0), LocalTime.of(22, 0)),
                    new TimeWindow(DayOfWeek.WEDNESDAY, LocalTime.of(20, 0), LocalTime.of(23, 0))),
            ZoneId.of("Europe/Madrid"));

    @Autowired
    private Characters characters;

    @Autowired
    private Listings listings;

    @Autowired
    private BoardListings board;

    @Autowired
    private JdbcTemplate jdbc;

    @AfterEach
    void emptyTheBoard() {
        jdbc.execute("delete from characters");
    }

    @Test
    void returnsOnlyActiveUnexpiredListingsInTheRegionAndBracket() {
        Character active = savedCharacter("Frostbolt", Region.EU, WowClass.MAGE, 675,
                Set.of(Specialization.MAGE_FROST), Map.of());
        LfgListing included = publish(active, Bracket.THREE_VS_THREE, NOW.minus(Duration.ofHours(1)));

        Character otherBracket = savedCharacter("Shadow", Region.EU, WowClass.PRIEST, 675,
                Set.of(Specialization.PRIEST_SHADOW), Map.of());
        publish(otherBracket, Bracket.TWO_VS_TWO, NOW.minus(Duration.ofHours(1)));

        Character otherRegion = savedCharacter("Arrow", Region.US, WowClass.HUNTER, 675,
                Set.of(Specialization.HUNTER_MARKSMANSHIP), Map.of());
        publish(otherRegion, Bracket.THREE_VS_THREE, NOW.minus(Duration.ofHours(1)));

        Character overdue = savedCharacter("Rogue", Region.EU, WowClass.ROGUE, 675,
                Set.of(Specialization.ROGUE_SUBTLETY), Map.of());
        publish(overdue, Bracket.THREE_VS_THREE, NOW.minus(Duration.ofHours(48)));

        Character cancelled = savedCharacter("Bear", Region.EU, WowClass.DRUID, 675,
                Set.of(Specialization.DRUID_GUARDIAN), Map.of());
        LfgListing cancelledListing = LfgListing.publish(cancelled.id(), Bracket.THREE_VS_THREE,
                Specialization.DRUID_GUARDIAN, Set.of(Language.ES), SCHEDULE,
                NOW.minus(Duration.ofHours(1)), TTL);
        cancelledListing.cancel();
        listings.publish(cancelledListing);

        assertThat(board.search(criteria(null, null, null)))
                .extracting(candidate -> candidate.listing().id())
                .containsExactly(included.id());
    }

    @Test
    void filtersByClassAndMinimumItemLevel() {
        Character gearedMage = savedCharacter("Frostbolt", Region.EU, WowClass.MAGE, 680,
                Set.of(Specialization.MAGE_FROST), Map.of());
        LfgListing included = publish(gearedMage, Bracket.THREE_VS_THREE,
                NOW.minus(Duration.ofHours(1)));

        Character undergearedMage = savedCharacter("Ember", Region.EU, WowClass.MAGE, 650,
                Set.of(Specialization.MAGE_FIRE), Map.of());
        publish(undergearedMage, Bracket.THREE_VS_THREE, NOW.minus(Duration.ofHours(1)));

        Character gearedPriest = savedCharacter("Shadow", Region.EU, WowClass.PRIEST, 680,
                Set.of(Specialization.PRIEST_SHADOW), Map.of());
        publish(gearedPriest, Bracket.THREE_VS_THREE, NOW.minus(Duration.ofHours(1)));

        assertThat(board.search(criteria(WowClass.MAGE, null, new ItemLevel(670))))
                .extracting(candidate -> candidate.listing().id())
                .containsExactly(included.id());
    }

    @Test
    void aMinimumRatingAcceptsOnlyCurrentSeasonFreshStandingsAndOldEvidenceStaysVisible() {
        Character fresh = ratingCharacter("Fresh",
                standing(1900, CURRENT_SEASON, NOW.minus(Duration.ofHours(1))));
        LfgListing freshListing = publish(fresh, Bracket.THREE_VS_THREE, NOW.minus(Duration.ofHours(1)));

        Character boundary = ratingCharacter("Boundary",
                standing(1800, CURRENT_SEASON, FRESH_AFTER));
        LfgListing boundaryListing = publish(boundary, Bracket.THREE_VS_THREE,
                NOW.minus(Duration.ofHours(1)));

        Character low = ratingCharacter("Low",
                standing(1750, CURRENT_SEASON, NOW.minus(Duration.ofHours(1))));
        publish(low, Bracket.THREE_VS_THREE, NOW.minus(Duration.ofHours(1)));

        Character stale = ratingCharacter("Stale",
                standing(2400, CURRENT_SEASON, FRESH_AFTER.minusSeconds(1)));
        publish(stale, Bracket.THREE_VS_THREE, NOW.minus(Duration.ofHours(1)));

        Character previousSeason = ratingCharacter("Previous",
                standing(2400, new PvpSeason(41), NOW.minus(Duration.ofHours(1))));
        publish(previousSeason, Bracket.THREE_VS_THREE, NOW.minus(Duration.ofHours(1)));

        Character unranked = savedCharacter("Unranked", Region.EU, WowClass.MAGE, 675,
                Set.of(Specialization.MAGE_FROST), Map.of());
        publish(unranked, Bracket.THREE_VS_THREE, NOW.minus(Duration.ofHours(1)));

        assertThat(board.search(criteria(null, new Rating(1800), null)))
                .as("stale, previous-season and unranked evidence cannot satisfy the filter")
                .extracting(candidate -> candidate.listing().id())
                .containsExactlyInAnyOrder(freshListing.id(), boundaryListing.id());

        assertThat(board.search(criteria(null, null, null)))
                .as("without a minimum the old evidence remains visible")
                .hasSize(6);
    }

    @Test
    void ordersByCreationTimeNewestFirst() {
        Character oldest = savedCharacter("Oldest", Region.EU, WowClass.MAGE, 675,
                Set.of(Specialization.MAGE_FROST), Map.of());
        Character middle = savedCharacter("Middle", Region.EU, WowClass.WARRIOR, 675,
                Set.of(Specialization.WARRIOR_ARMS), Map.of());
        Character newest = savedCharacter("Newest", Region.EU, WowClass.SHAMAN, 675,
                Set.of(Specialization.SHAMAN_ELEMENTAL), Map.of());

        LfgListing oldestListing = publish(oldest, Bracket.THREE_VS_THREE, NOW.minus(Duration.ofHours(3)));
        LfgListing middleListing = publish(middle, Bracket.THREE_VS_THREE, NOW.minus(Duration.ofHours(2)));
        LfgListing newestListing = publish(newest, Bracket.THREE_VS_THREE, NOW.minus(Duration.ofHours(1)));

        assertThat(board.search(criteria(null, null, null)))
                .extracting(candidate -> candidate.listing().id())
                .containsExactly(newestListing.id(), middleListing.id(), oldestListing.id());
    }

    @Test
    void hydratesTheWholeAggregateAndThePinnedListingFacts() {
        Character character = Character.register(PLAYER,
                new CharacterName("Frostbolt"),
                new Realm("Sanguino"),
                Region.EU,
                WowClass.MAGE,
                new GearSnapshot(new ItemLevel(675), GEAR_OBSERVED_AT),
                Set.of(Specialization.MAGE_FROST, Specialization.MAGE_ARCANE),
                Map.of(Bracket.TWO_VS_TWO,
                                standing(1800, CURRENT_SEASON, NOW.minus(Duration.ofDays(10))),
                        Bracket.THREE_VS_THREE,
                                standing(2100, CURRENT_SEASON, NOW.minus(Duration.ofDays(1)))));
        characters.save(character);

        LfgListing listing = LfgListing.publish(character.id(), Bracket.THREE_VS_THREE,
                Specialization.MAGE_FROST, Set.of(Language.ES, Language.EN), SCHEDULE,
                NOW.minus(Duration.ofHours(1)), TTL);
        listings.publish(listing);

        List<BoardCandidate> results = board.search(criteria(null, null, null));

        assertThat(results).singleElement().satisfies(candidate -> {
            LfgListing found = candidate.listing();
            assertThat(found.id()).isEqualTo(listing.id());
            assertThat(found.spec()).isEqualTo(Specialization.MAGE_FROST);
            assertThat(found.languages()).containsExactlyInAnyOrder(Language.ES, Language.EN);
            assertThat(found.schedule()).isEqualTo(SCHEDULE);

            Character hydrated = candidate.character();
            assertThat(hydrated.id()).isEqualTo(character.id());
            assertThat(hydrated.ownerId()).isEqualTo(PLAYER);
            assertThat(hydrated.name().value()).isEqualTo("Frostbolt");
            assertThat(hydrated.realm()).isEqualTo(new Realm("Sanguino"));
            assertThat(hydrated.region()).isEqualTo(Region.EU);
            assertThat(hydrated.wowClass()).isEqualTo(WowClass.MAGE);
            assertThat(hydrated.specs()).containsExactlyInAnyOrder(
                    Specialization.MAGE_FROST, Specialization.MAGE_ARCANE);
            assertThat(hydrated.gear()).isEqualTo(new GearSnapshot(new ItemLevel(675), GEAR_OBSERVED_AT));
            assertThat(hydrated.ratingFor(Bracket.TWO_VS_TWO)).contains(new Rating(1800));
            assertThat(hydrated.ratingFor(Bracket.THREE_VS_THREE)).contains(new Rating(2100));
            assertThat(hydrated.standingFor(Bracket.THREE_VS_THREE).orElseThrow().observedAt())
                    .isEqualTo(NOW.minus(Duration.ofDays(1)));
        });
    }

    private static SearchCriteria criteria(WowClass wowClass, Rating minimumRating,
                                           ItemLevel minimumItemLevel) {
        return new SearchCriteria(Region.EU, Bracket.THREE_VS_THREE, wowClass, minimumRating,
                minimumItemLevel, CURRENT_SEASON, FRESH_AFTER, NOW);
    }

    private Character ratingCharacter(String name, PvpStanding standing) {
        return savedCharacter(name, Region.EU, WowClass.MAGE, 675,
                Set.of(Specialization.MAGE_FROST), Map.of(Bracket.THREE_VS_THREE, standing));
    }

    private Character savedCharacter(String name, Region region, WowClass wowClass, int itemLevel,
                                     Set<Specialization> specs,
                                     Map<Bracket, PvpStanding> standings) {
        Character character = Character.register(PLAYER,
                new CharacterName(name),
                new Realm("Sanguino"),
                region,
                wowClass,
                new GearSnapshot(new ItemLevel(itemLevel), GEAR_OBSERVED_AT),
                specs,
                standings);
        characters.save(character);
        return character;
    }

    private LfgListing publish(Character character, Bracket bracket, Instant createdAt) {
        return publish(character, bracket, character.specs().iterator().next(), createdAt);
    }

    private LfgListing publish(Character character, Bracket bracket, Specialization spec,
                               Instant createdAt) {
        LfgListing listing = LfgListing.publish(character.id(), bracket, spec,
                Set.of(Language.ES, Language.EN), SCHEDULE, createdAt, TTL);
        listings.publish(listing);
        return listing;
    }

    private PvpStanding standing(int rating, PvpSeason season, Instant observedAt) {
        return new PvpStanding(new Rating(rating), season, observedAt);
    }
}
