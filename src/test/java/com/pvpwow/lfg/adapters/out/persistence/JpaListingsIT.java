package com.pvpwow.lfg.adapters.out.persistence;

import com.pvpwow.lfg.application.port.out.Characters;
import com.pvpwow.lfg.application.port.out.ListingLifecycle;
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
import com.pvpwow.lfg.domain.join.JoinRequest;
import com.pvpwow.lfg.domain.listing.AvailabilitySchedule;
import com.pvpwow.lfg.domain.listing.Language;
import com.pvpwow.lfg.domain.listing.LfgListing;
import com.pvpwow.lfg.domain.listing.ListingStatus;
import com.pvpwow.lfg.domain.listing.TimeWindow;
import com.pvpwow.lfg.domain.player.PlayerId;
import com.pvpwow.lfg.domain.shared.DomainRuleViolationException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

import java.sql.Timestamp;
import java.time.DayOfWeek;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Listing writes against the REAL database: the transaction that expires and
 * inserts, the mapping across the root and its two child tables, and the
 * partial unique index that the application-level pre-check cannot replace.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@Testcontainers
class JpaListingsIT {

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
    private ListingLifecycle lifecycle;

    @Autowired
    private ListingJpaRepository repository;

    @Autowired
    private JdbcTemplate jdbc;

    @AfterEach
    void emptyTheBoard() {
        repository.deleteAll();
        jdbc.execute("delete from characters");
    }

    @Test
    void publishesTheAggregateAcrossTheRootAndItsChildTables() {
        Character character = savedCharacter("Frostbolt");

        LfgListing listing = publish(character, Bracket.THREE_VS_THREE, NOW);

        assertThat(countIn("lfg_listings")).isEqualTo(1);
        assertThat(countIn("listing_languages")).isEqualTo(2);
        assertThat(countIn("listing_windows")).isEqualTo(2);
        assertThat(jdbc.queryForObject(
                "select status from lfg_listings", String.class)).isEqualTo("ACTIVE");
        assertThat(jdbc.queryForObject(
                "select availability_zone from lfg_listings", String.class)).isEqualTo("Europe/Madrid");
        assertThat(listing.expiresAt()).isEqualTo(NOW.plus(TTL));
    }

    @Test
    @Transactional
    void rehydratesTheListingWithItsLanguagesScheduleAndPinnedSpec() {
        Character character = savedCharacter("Frostbolt");
        LfgListing listing = publish(character, Bracket.THREE_VS_THREE, NOW);

        ListingEntity entity = repository.findById(listing.id().value()).orElseThrow();
        LfgListing rehydrated = ListingEntityMapper.toDomain(entity);

        assertThat(rehydrated.id()).isEqualTo(listing.id());
        assertThat(rehydrated.characterId()).isEqualTo(character.id());
        assertThat(rehydrated.bracket()).isEqualTo(Bracket.THREE_VS_THREE);
        assertThat(rehydrated.spec()).isEqualTo(Specialization.MAGE_FROST);
        assertThat(rehydrated.languages()).containsExactlyInAnyOrder(Language.ES, Language.EN);
        assertThat(rehydrated.schedule()).isEqualTo(SCHEDULE);
        assertThat(rehydrated.status()).isEqualTo(ListingStatus.ACTIVE);
        assertThat(rehydrated.createdAt()).isEqualTo(NOW);
        assertThat(rehydrated.expiresAt()).isEqualTo(NOW.plus(TTL));
    }

    @Test
    void publishingAgainExpiresTheDueRowBeforeInsertingTheNewOne() {
        Character character = savedCharacter("Frostbolt");
        LfgListing first = publish(character, Bracket.THREE_VS_THREE, NOW);

        LfgListing second = publish(character, Bracket.THREE_VS_THREE, NOW.plus(Duration.ofHours(25)));

        assertThat(countIn("lfg_listings")).isEqualTo(2);
        assertThat(statusOf(first)).isEqualTo("EXPIRED");
        assertThat(statusOf(second)).isEqualTo("ACTIVE");
        assertThat(listings.hasActiveFor(character.id(), Bracket.THREE_VS_THREE,
                second.createdAt())).isTrue();
    }

    @Test
    void anOverdueActiveRowDoesNotCountAsActive() {
        Character character = savedCharacter("Frostbolt");
        publish(character, Bracket.THREE_VS_THREE, NOW);

        assertThat(listings.hasActiveFor(character.id(), Bracket.THREE_VS_THREE,
                NOW.plus(TTL).minusSeconds(1))).isTrue();
        assertThat(listings.hasActiveFor(character.id(), Bracket.THREE_VS_THREE,
                NOW.plus(TTL))).as("at exactly expiresAt the listing is due").isFalse();
    }

    @Test
    void thePartialUniqueIndexIsTranslatedIntoTheBusinessRule() {
        Character character = savedCharacter("Frostbolt");
        publish(character, Bracket.THREE_VS_THREE, NOW);
        LfgListing second = LfgListing.publish(character.id(), Bracket.THREE_VS_THREE,
                Specialization.MAGE_FROST, Set.of(Language.ES), SCHEDULE,
                NOW.plus(Duration.ofHours(1)), TTL);

        assertThatThrownBy(() -> listings.publish(second))
                .isInstanceOf(DomainRuleViolationException.class)
                .hasMessageContaining("already has an active");

        assertThat(countIn("lfg_listings")).isEqualTo(1);
    }

    @Test
    void refusesToPublishAListingThatIsNotActive() {
        Character character = savedCharacter("Frostbolt");
        LfgListing cancelled = LfgListing.publish(character.id(), Bracket.THREE_VS_THREE,
                Specialization.MAGE_FROST, Set.of(Language.ES), SCHEDULE, NOW, TTL);
        cancelled.cancel();

        assertThatThrownBy(() -> listings.publish(cancelled))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Only an active listing");

        assertThat(countIn("lfg_listings")).isZero();
    }

    @Test
    void theDatabaseStillEnforcesUniquenessWhenTheAdapterSweepIsSkipped() {
        Character character = savedCharacter("Frostbolt");
        publish(character, Bracket.THREE_VS_THREE, NOW);

        assertThatThrownBy(() -> jdbc.update("""
                        insert into lfg_listings
                            (id, character_id, bracket, spec, status, availability_zone,
                             created_at, expires_at)
                        values (?, ?, ?, ?, ?, ?, ?, ?)
                        """,
                UUID.randomUUID(), character.id().value(), "THREE_VS_THREE", "MAGE_FROST",
                "ACTIVE", "Europe/Madrid",
                Timestamp.from(NOW.plus(Duration.ofHours(1))),
                Timestamp.from(NOW.plus(Duration.ofHours(25)))))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void publishingOverADueListingRecordsItsExpiredFact() {
        Character character = savedCharacter("Frostbolt");
        LfgListing first = publish(character, Bracket.THREE_VS_THREE, NOW);

        publish(character, Bracket.THREE_VS_THREE, NOW.plus(Duration.ofHours(25)));

        assertThat(kindOfOutcome(first))
                .as("a listing replaced by a new publication is still a fact of the metrics")
                .isEqualTo("EXPIRED");
        assertThat(countIn("listing_outcomes")).isEqualTo(1);
    }

    @Test
    void publishingOverADueListingCancelsItsPendingRequests() {
        Character character = savedCharacter("Frostbolt");
        Character stranger = savedCharacter("Ember");
        LfgListing first = publish(character, Bracket.THREE_VS_THREE, NOW);
        // Somebody asked to join the listing that is about to be replaced.
        JoinRequest request = JoinRequest.request(stranger.id(), first.id(), NOW,
                first.expiresAt());
        lifecycle.save(request);

        publish(character, Bracket.THREE_VS_THREE, NOW.plus(Duration.ofHours(25)));

        assertThat(statusOfRequest(request))
                .as("the request was waiting for an answer that can never come")
                .isEqualTo("CANCELLED");
    }

    private LfgListing publish(Character character, Bracket bracket, Instant createdAt) {
        LfgListing listing = LfgListing.publish(character.id(), bracket,
                Specialization.MAGE_FROST, Set.of(Language.ES, Language.EN), SCHEDULE,
                createdAt, TTL);
        listings.publish(listing);
        return listing;
    }

    private Character savedCharacter(String name) {
        Character character = Character.register(PLAYER,
                new CharacterName(name),
                new Realm("Sanguino"),
                Region.EU,
                WowClass.MAGE,
                new GearSnapshot(new ItemLevel(675), NOW),
                Set.of(Specialization.MAGE_FROST, Specialization.MAGE_ARCANE),
                Map.of(Bracket.THREE_VS_THREE,
                        new PvpStanding(new Rating(2100), new PvpSeason(42), NOW)));
        characters.save(character);
        return character;
    }

    private String statusOf(LfgListing listing) {
        return jdbc.queryForObject("select status from lfg_listings where id = ?",
                String.class, listing.id().value());
    }

    private String kindOfOutcome(LfgListing listing) {
        return jdbc.queryForObject("select kind from listing_outcomes where listing_id = ?",
                String.class, listing.id().value());
    }

    private String statusOfRequest(JoinRequest request) {
        return jdbc.queryForObject("select status from join_requests where id = ?",
                String.class, request.id().value());
    }

    private int countIn(String table) {
        return jdbc.queryForObject("select count(*) from " + table, Integer.class);
    }
}
