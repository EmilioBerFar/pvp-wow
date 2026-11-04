package com.pvpwow.lfg.adapters.out.persistence;

import com.pvpwow.lfg.application.port.in.RunRetentionSweep;
import com.pvpwow.lfg.application.port.in.RunRetentionSweep.SweepReport;
import com.pvpwow.lfg.application.port.out.Characters;
import com.pvpwow.lfg.application.port.out.ListingLifecycle;
import com.pvpwow.lfg.application.port.out.ListingRetention;
import com.pvpwow.lfg.application.port.out.Listings;
import com.pvpwow.lfg.application.port.out.Players;
import com.pvpwow.lfg.domain.character.Bracket;
import com.pvpwow.lfg.domain.character.Character;
import com.pvpwow.lfg.domain.character.CharacterId;
import com.pvpwow.lfg.domain.character.CharacterName;
import com.pvpwow.lfg.domain.character.GearSnapshot;
import com.pvpwow.lfg.domain.character.ItemLevel;
import com.pvpwow.lfg.domain.character.Realm;
import com.pvpwow.lfg.domain.character.Region;
import com.pvpwow.lfg.domain.character.Specialization;
import com.pvpwow.lfg.domain.character.WowClass;
import com.pvpwow.lfg.domain.join.JoinRequest;
import com.pvpwow.lfg.domain.listing.AvailabilitySchedule;
import com.pvpwow.lfg.domain.listing.Language;
import com.pvpwow.lfg.domain.listing.LfgListing;
import com.pvpwow.lfg.domain.listing.ListingId;
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

import java.time.Clock;
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
 * The retention sweep against the REAL database: overdue listings closed
 * through the shared closer (fact + pending requests), closed rows deleted
 * after the window, facts kept.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@Testcontainers
class JpaListingRetentionIT {

    @Container
    static PostgreSQLContainer postgres =
            new PostgreSQLContainer(DockerImageName.parse("postgres:18-alpine"));

    @DynamicPropertySource
    static void pointTheApplicationAtTheContainer(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    private static final Instant NOW = Instant.parse("2026-11-03T10:00:00Z");
    private static final Duration TTL = Duration.ofHours(24);
    private static final PlayerId OWNER = new PlayerId(UUID.randomUUID());
    private static final PlayerId REQUESTER_OWNER = new PlayerId(UUID.randomUUID());
    private static final AvailabilitySchedule SCHEDULE = new AvailabilitySchedule(
            Set.of(new TimeWindow(DayOfWeek.MONDAY, LocalTime.of(18, 0), LocalTime.of(22, 0))),
            ZoneId.of("Europe/Madrid"));

    @Autowired
    private Characters characters;

    @Autowired
    private Players players;

    @Autowired
    private Listings listings;

    @Autowired
    private ListingLifecycle lifecycle;

    @Autowired
    private ListingRetention retention;

    @Autowired
    private RunRetentionSweep sweep;

    @Autowired
    private Clock clock;

    @Autowired
    private JdbcTemplate jdbc;

    @AfterEach
    void emptyTheBoard() {
        jdbc.execute("delete from join_requests");
        jdbc.execute("delete from listing_outcomes");
        jdbc.execute("delete from lfg_listings");
        jdbc.execute("delete from characters");
        jdbc.execute("delete from players");
    }

    @Test
    void anOverdueListingIsClosedWithItsFactAndItsRequests() {
        Character owner = savedCharacter("Frostbolt", OWNER);
        Character stranger = savedCharacter("Shadow", new PlayerId(UUID.randomUUID()));
        LfgListing overdue = publish(owner, Bracket.THREE_VS_THREE, NOW);
        JoinRequest request = pendingRequest(stranger.id(), overdue, OWNER);

        int closed = retention.expireOverdue(NOW.plus(Duration.ofHours(25)), 100);

        assertThat(closed).isEqualTo(1);
        assertThat(statusOf(overdue.id())).isEqualTo("EXPIRED");
        assertThat(kindOf(overdue.id()))
                .as("a listing the clock killed still reaches the metrics")
                .isEqualTo("EXPIRED");
        assertThat(statusOfRequest(request.id()))
                .as("the player who asked gets the slot back")
                .isEqualTo("CANCELLED");
    }

    @Test
    void aListingThatIsNotDueYetIsLeftAlone() {
        Character owner = savedCharacter("Frostbolt", OWNER);
        LfgListing live = publish(owner, Bracket.THREE_VS_THREE, NOW);

        int closed = retention.expireOverdue(NOW.plus(Duration.ofHours(23)), 100);

        assertThat(closed).isZero();
        assertThat(statusOf(live.id())).isEqualTo("ACTIVE");
        assertThat(countIn("listing_outcomes")).isZero();
    }

    @Test
    void theSweepClosesEveryPage() {
        Character first = savedCharacter("Frostbolt", OWNER);
        Character second = savedCharacter("Ember", REQUESTER_OWNER);
        Character third = savedCharacter("Shadow", new PlayerId(UUID.randomUUID()));
        LfgListing one = publish(first, Bracket.THREE_VS_THREE, NOW);
        LfgListing two = publish(second, Bracket.TWO_VS_TWO, NOW);
        LfgListing three = publish(third, Bracket.THREE_VS_THREE, NOW);
        Instant later = NOW.plus(Duration.ofHours(25));

        assertThat(retention.expireOverdue(later, 2)).isEqualTo(2);
        assertThat(retention.expireOverdue(later, 2))
                .as("the second page holds the remaining row")
                .isEqualTo(1);
        assertThat(retention.expireOverdue(later, 2)).isZero();

        assertThat(List.of(statusOf(one.id()), statusOf(two.id()), statusOf(three.id())))
                .containsOnly("EXPIRED");
        assertThat(countIn("listing_outcomes")).isEqualTo(3);
    }

    @Test
    void aClosedListingPastTheWindowIsDeletedAndItsFactStays() {
        Character owner = savedCharacter("Frostbolt", OWNER);
        Character stranger = savedCharacter("Shadow", new PlayerId(UUID.randomUUID()));
        LfgListing listing = publish(owner, Bracket.THREE_VS_THREE, NOW);
        JoinRequest request = pendingRequest(stranger.id(), listing, OWNER);
        lifecycle.withdraw(listing.id(), owner.id(), NOW);

        int deleted = retention.deleteClosedBefore(
                listing.expiresAt().plusSeconds(1), 100);

        assertThat(deleted).isEqualTo(1);
        assertThat(countIn("lfg_listings")).isZero();
        assertThat(countIn("listing_outcomes"))
                .as("the fact outlives the row it describes")
                .isEqualTo(1);
        assertThat(countIn("join_requests"))
                .as("the requests die with the listing, through the foreign key")
                .isZero();
    }

    @Test
    void aClosedListingInsideTheWindowIsKept() {
        Character owner = savedCharacter("Frostbolt", OWNER);
        LfgListing listing = publish(owner, Bracket.THREE_VS_THREE, NOW);
        lifecycle.withdraw(listing.id(), owner.id(), NOW);

        int deleted = retention.deleteClosedBefore(listing.expiresAt().minusSeconds(1), 100);

        assertThat(deleted).isZero();
        assertThat(statusOf(listing.id())).isEqualTo("CANCELLED");
    }

    @Test
    void aCompletedListingPastTheWindowIsDeletedAndItsFactStays() {
        Character owner = savedCharacter("Frostbolt", OWNER);
        Character requester = savedCharacter("Ember", REQUESTER_OWNER);
        LfgListing listing = publish(owner, Bracket.THREE_VS_THREE, NOW);
        JoinRequest request = pendingRequest(requester.id(), listing, OWNER);
        lifecycle.completeGroup(request, owner.id(), requester.id(), OWNER,
                REQUESTER_OWNER, NOW);

        // The cutoff is `now - window`: before expiresAt the listing is still
        // inside the window; past it, the sweep may delete it.
        int insideTheWindow = retention.deleteClosedBefore(
                listing.expiresAt().minusSeconds(1), 100);

        assertThat(insideTheWindow).isZero();
        assertThat(statusOf(listing.id())).isEqualTo("COMPLETED");

        int pastTheWindow = retention.deleteClosedBefore(
                listing.expiresAt().plusSeconds(1), 100);

        assertThat(pastTheWindow)
                .as("only ACTIVE survives: a completed listing is not history, its fact is")
                .isEqualTo(1);
        assertThat(countOf(listing.id())).isZero();
        assertThat(kindOf(listing.id()))
                .as("the fact is the single source of the success rate")
                .isEqualTo("COMPLETED");
    }

    @Test
    void expiredPendingRequestsAreMarkedExpired() {
        Character owner = savedCharacter("Frostbolt", OWNER);
        Character requester = savedCharacter("Ember", REQUESTER_OWNER);
        LfgListing listing = publish(owner, Bracket.THREE_VS_THREE, NOW);
        JoinRequest request = pendingRequest(requester.id(), listing, OWNER);

        int expired = retention.expirePendingRequests(NOW.plus(Duration.ofHours(25)));

        assertThat(expired).isEqualTo(1);
        assertThat(statusOfRequest(request.id())).isEqualTo("EXPIRED");
    }

    @Test
    void theWholeSweepClosesDeletesAndLeavesTheLiveBoardAlone() {
        Instant now = clock.instant();
        Character owner = savedCharacter("Frostbolt", OWNER);
        Character other = savedCharacter("Ember", REQUESTER_OWNER);
        // Due one day ago: the close pass must take it.
        LfgListing overdue = publish(owner, Bracket.THREE_VS_THREE,
                now.minus(Duration.ofDays(2)));
        // Cancelled 39 days ago: past the 30-day window, the delete pass takes it.
        LfgListing ancient = publish(owner, Bracket.TWO_VS_TWO,
                now.minus(Duration.ofDays(40)));
        lifecycle.withdraw(ancient.id(), owner.id(), now);
        // Still live: untouched.
        LfgListing live = publish(other, Bracket.TWO_VS_TWO, now);

        SweepReport report = sweep.sweep();

        assertThat(report.expiredListings()).isEqualTo(1);
        assertThat(report.deletedListings()).isEqualTo(1);
        assertThat(statusOf(overdue.id())).isEqualTo("EXPIRED");
        assertThat(kindOf(overdue.id())).isEqualTo("EXPIRED");
        assertThat(countOf(ancient.id())).isZero();
        assertThat(kindOf(ancient.id()))
                .as("the deleted listing keeps its fact")
                .isEqualTo("CANCELLED");
        assertThat(statusOf(live.id())).isEqualTo("ACTIVE");
    }

    private Character savedCharacter(String name, PlayerId owner) {
        PlayersFixture.ensure(players, owner);
        Character character = Character.register(owner, new CharacterName(name),
                new Realm("Sanguino"), Region.EU, WowClass.MAGE,
                new GearSnapshot(new ItemLevel(675), NOW),
                Set.of(Specialization.MAGE_FROST), Map.of());
        characters.save(character);
        return character;
    }

    private LfgListing publish(Character character, Bracket bracket, Instant createdAt) {
        LfgListing listing = LfgListing.publish(character.id(), bracket,
                Specialization.MAGE_FROST, Set.of(Language.ES), SCHEDULE, createdAt, TTL);
        listings.publish(listing);
        return listing;
    }

    private JoinRequest pendingRequest(CharacterId requesterId, LfgListing listing,
                                       PlayerId ownerId) {
        JoinRequest request = JoinRequest.request(requesterId, listing.id(), NOW,
                listing.expiresAt());
        lifecycle.save(request);
        return request;
    }

    private int countIn(String table) {
        return jdbc.queryForObject("select count(*) from " + table, Integer.class);
    }

    private int countOf(ListingId listingId) {
        return jdbc.queryForObject("select count(*) from lfg_listings where id = ?",
                Integer.class, listingId.value());
    }

    private String statusOf(ListingId listingId) {
        return jdbc.queryForObject("select status from lfg_listings where id = ?",
                String.class, listingId.value());
    }

    private String kindOf(ListingId listingId) {
        return jdbc.queryForObject("select kind from listing_outcomes where listing_id = ?",
                String.class, listingId.value());
    }

    private String statusOfRequest(com.pvpwow.lfg.domain.join.JoinRequestId joinRequestId) {
        return jdbc.queryForObject("select status from join_requests where id = ?",
                String.class, joinRequestId.value());
    }
}
