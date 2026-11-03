package com.pvpwow.lfg.adapters.out.persistence;

import com.pvpwow.lfg.application.port.out.Characters;
import com.pvpwow.lfg.application.port.out.ListingLifecycle;
import com.pvpwow.lfg.application.port.out.Listings;
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
import com.pvpwow.lfg.domain.join.JoinRequestStatus;
import com.pvpwow.lfg.domain.listing.AvailabilitySchedule;
import com.pvpwow.lfg.domain.listing.Language;
import com.pvpwow.lfg.domain.listing.LfgListing;
import com.pvpwow.lfg.domain.listing.ListingOutcome;
import com.pvpwow.lfg.domain.listing.ListingOutcomeKind;
import com.pvpwow.lfg.domain.listing.ListingStatus;
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
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The group handshake against the REAL database: closing both players'
 * listings, writing their facts and cancelling the pending requests — all of
 * it in one transaction.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@Testcontainers
class JpaListingLifecycleIT {

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
    private Listings listings;

    @Autowired
    private ListingLifecycle lifecycle;

    @Autowired
    private JdbcTemplate jdbc;

    @AfterEach
    void emptyTheBoard() {
        jdbc.execute("delete from join_requests");
        jdbc.execute("delete from listing_outcomes");
        jdbc.execute("delete from lfg_listings");
        jdbc.execute("delete from characters");
    }

    @Test
    void acceptingClosesBothPlayersListingsAndWritesTheirFacts() {
        Character owner = savedCharacter("Frostbolt", OWNER);
        Character requester = savedCharacter("Ember", REQUESTER_OWNER);
        LfgListing ownerListing = publish(owner, Bracket.THREE_VS_THREE);
        LfgListing requesterListing = publish(requester, Bracket.TWO_VS_TWO);
        JoinRequest request = pendingRequest(requester.id(), ownerListing, OWNER);

        List<ListingOutcome> facts = lifecycle.completeGroup(request, owner.id(),
                requester.id(), REQUESTER_OWNER, NOW);

        assertThat(facts).hasSize(2);
        assertThat(statusOf(ownerListing.id())).isEqualTo("COMPLETED");
        assertThat(statusOf(requesterListing.id())).isEqualTo("COMPLETED");
        assertThat(countIn("listing_outcomes")).isEqualTo(2);
        assertThat(kindOf(ownerListing.id())).isEqualTo("COMPLETED");
        assertThat(characterOf(ownerListing.id())).isEqualTo(owner.id().value());
        assertThat(characterOf(requesterListing.id())).isEqualTo(requester.id().value());
    }

    @Test
    void acceptingClosesEveryListingOfBothPlayersInEveryBracket() {
        Character owner = savedCharacter("Frostbolt", OWNER);
        Character requester = savedCharacter("Ember", REQUESTER_OWNER);
        LfgListing threeVThree = publish(owner, Bracket.THREE_VS_THREE);
        LfgListing twoVTwo = publish(owner, Bracket.TWO_VS_TWO);
        publish(requester, Bracket.THREE_VS_THREE);
        JoinRequest request = pendingRequest(requester.id(), threeVThree, OWNER);

        lifecycle.completeGroup(request, owner.id(), requester.id(), REQUESTER_OWNER, NOW);

        assertThat(statusOf(threeVThree.id())).isEqualTo("COMPLETED");
        assertThat(statusOf(twoVTwo.id())).isEqualTo("COMPLETED");
        assertThat(countIn("listing_outcomes")).isEqualTo(3);
    }

    @Test
    void acceptingCancelsEveryPendingRequestOfTheRequestersPlayer() {
        Character owner = savedCharacter("Frostbolt", OWNER);
        Character requester = savedCharacter("Ember", REQUESTER_OWNER);
        LfgListing first = publish(owner, Bracket.THREE_VS_THREE);
        LfgListing second = publish(owner, Bracket.TWO_VS_TWO);
        JoinRequest accepted = pendingRequest(requester.id(), first, OWNER);
        JoinRequest other = pendingRequest(requester.id(), second, OWNER);

        lifecycle.completeGroup(accepted, owner.id(), requester.id(), REQUESTER_OWNER, NOW);

        assertThat(statusOfRequest(accepted.id())).isEqualTo("ACCEPTED");
        assertThat(statusOfRequest(other.id()))
                .as("starting to play closes every pending request of the player")
                .isEqualTo("CANCELLED");
    }

    @Test
    void theListingsOfSomebodyElseAreNotTouched() {
        Character owner = savedCharacter("Frostbolt", OWNER);
        Character requester = savedCharacter("Ember", REQUESTER_OWNER);
        Character stranger = savedCharacter("Shadow", new PlayerId(UUID.randomUUID()));
        LfgListing ownerListing = publish(owner, Bracket.THREE_VS_THREE);
        LfgListing strangerListing = publish(stranger, Bracket.THREE_VS_THREE);
        JoinRequest request = pendingRequest(requester.id(), ownerListing, OWNER);

        lifecycle.completeGroup(request, owner.id(), requester.id(), REQUESTER_OWNER, NOW);

        assertThat(statusOf(strangerListing.id())).isEqualTo("ACTIVE");
        assertThat(countIn("listing_outcomes")).isEqualTo(1);
    }

    @Test
    void aFactSurvivesTheDeletionOfItsCharacter() {
        Character owner = savedCharacter("Frostbolt", OWNER);
        Character requester = savedCharacter("Ember", REQUESTER_OWNER);
        LfgListing ownerListing = publish(owner, Bracket.THREE_VS_THREE);
        JoinRequest request = pendingRequest(requester.id(), ownerListing, OWNER);

        lifecycle.completeGroup(request, owner.id(), requester.id(), REQUESTER_OWNER, NOW);

        jdbc.update("delete from characters where id = ?", owner.id().value());

        assertThat(countIn("listing_outcomes"))
                .as("the history of what they published outlives the player")
                .isEqualTo(1);
        assertThat(characterOf(ownerListing.id()))
                .as("the fact keeps the row but loses the player")
                .isNull();
    }

    @Test
    void withdrawingClosesTheListingAndRecordsACancelled() {
        Character owner = savedCharacter("Frostbolt", OWNER);
        LfgListing listing = publish(owner, Bracket.THREE_VS_THREE);

        ListingOutcome outcome = lifecycle.withdraw(listing.id(), owner.id(), NOW);

        assertThat(outcome.kind()).isEqualTo(ListingOutcomeKind.CANCELLED);
        assertThat(statusOf(listing.id())).isEqualTo("CANCELLED");
        assertThat(kindOf(listing.id())).isEqualTo("CANCELLED");
    }

    @Test
    void theDatabaseRefusesTwoPendingRequestsForTheSameListingByTheSamePlayer() {
        Character owner = savedCharacter("Frostbolt", OWNER);
        Character requester = savedCharacter("Ember", REQUESTER_OWNER);
        Character otherCharacter = savedCharacter("Cinder", REQUESTER_OWNER);
        LfgListing listing = publish(owner, Bracket.THREE_VS_THREE);
        pendingRequest(requester.id(), listing, OWNER);

        assertThatThrownBy(() -> pendingRequest(otherCharacter.id(), listing, OWNER))
                .as("a player cannot ask twice for the same listing, not even with another character")
                .isInstanceOf(RuntimeException.class);
    }

    private Character savedCharacter(String name, PlayerId owner) {
        Character character = Character.register(owner, new CharacterName(name),
                new Realm("Sanguino"), Region.EU, WowClass.MAGE,
                new GearSnapshot(new ItemLevel(675), NOW),
                Set.of(Specialization.MAGE_FROST), Map.of());
        characters.save(character);
        return character;
    }

    private LfgListing publish(Character character, Bracket bracket) {
        LfgListing listing = LfgListing.publish(character.id(), bracket,
                Specialization.MAGE_FROST, Set.of(Language.ES), SCHEDULE, NOW, TTL);
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

    private String statusOf(com.pvpwow.lfg.domain.listing.ListingId listingId) {
        return jdbc.queryForObject(
                "select status from lfg_listings where id = ?", String.class, listingId.value());
    }

    private String kindOf(com.pvpwow.lfg.domain.listing.ListingId listingId) {
        return jdbc.queryForObject(
                "select kind from listing_outcomes where listing_id = ?", String.class,
                listingId.value());
    }

    private UUID characterOf(com.pvpwow.lfg.domain.listing.ListingId listingId) {
        return jdbc.queryForObject(
                "select character_id from listing_outcomes where listing_id = ?",
                UUID.class, listingId.value());
    }

    private String statusOfRequest(com.pvpwow.lfg.domain.join.JoinRequestId joinRequestId) {
        return jdbc.queryForObject(
                "select status from join_requests where id = ?", String.class,
                joinRequestId.value());
    }
}
