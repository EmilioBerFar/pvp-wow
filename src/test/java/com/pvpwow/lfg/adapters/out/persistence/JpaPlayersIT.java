package com.pvpwow.lfg.adapters.out.persistence;

import com.pvpwow.lfg.application.port.out.Characters;
import com.pvpwow.lfg.application.port.out.Players;
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
import com.pvpwow.lfg.domain.player.BattleTag;
import com.pvpwow.lfg.domain.player.Player;
import com.pvpwow.lfg.domain.player.PlayerId;
import com.pvpwow.lfg.domain.player.Username;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

import java.time.Instant;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The players table and the foreign key it enables, against the REAL database.
 * The point of the unit is the rule "a character has an owner that exists", and
 * that rule is enforced by Postgres, not by a service: an H2 imitation would
 * pass and still lie.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@Testcontainers
class JpaPlayersIT {

    @Container
    static PostgreSQLContainer postgres =
            new PostgreSQLContainer(DockerImageName.parse("postgres:18-alpine"));

    @DynamicPropertySource
    static void pointTheApplicationAtTheContainer(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    private static final Instant OBSERVED_AT = Instant.parse("2026-11-01T10:00:00Z");

    @Autowired
    private Players players;

    @Autowired
    private Characters characters;

    @Autowired
    private JdbcTemplate jdbc;

    @AfterEach
    void emptyTheDatabase() {
        jdbc.execute("delete from characters");
        jdbc.execute("delete from join_requests");
        jdbc.execute("delete from players");
    }

    @Test
    void savesAndRehydratesTheAccount() {
        Player player = Player.register(new Username("Emilio"),
                new BattleTag("Emilio#12345"));

        players.save(player);

        Player rehydrated = players.findById(player.id()).orElseThrow();
        assertThat(rehydrated.id()).isEqualTo(player.id());
        assertThat(rehydrated.username())
                .as("the display casing of the handle is preserved")
                .isEqualTo(new Username("Emilio"));
        assertThat(rehydrated.battleTag()).isEqualTo(new BattleTag("emilio#12345"));
    }

    @Test
    void findingAnUnknownPlayerReturnsEmpty() {
        assertThat(players.findById(new PlayerId(UUID.randomUUID()))).isEmpty();
    }

    @Test
    void savingTheSamePlayerTwiceUpdatesInsteadOfDuplicating() {
        Player player = Player.register(new Username("emilio"),
                new BattleTag("emilio#12345"));

        players.save(player);
        players.save(player);

        assertThat(countIn("players")).isEqualTo(1);
    }

    @Test
    void theTakenChecksSeeThroughUsernameCasingButNotBattleTagCasing() {
        players.save(Player.register(new Username("Emilio"), new BattleTag("Emilio#12345")));

        assertThat(players.usernameTaken(new Username("emilio"))).isTrue();
        assertThat(players.usernameTaken(new Username("someone"))).isFalse();
        assertThat(players.battleTagTaken(new BattleTag("EMILIO#12345")))
                .as("the value object folds the tag before it reaches the query")
                .isTrue();
    }

    @Test
    void theIndexRejectsADuplicateUsernameInAnotherCase() {
        players.save(Player.register(new Username("Emilio"), new BattleTag("someone#1234")));

        assertThatThrownBy(() -> players.save(Player.register(
                new Username("emilio"), new BattleTag("other#1234"))))
                .as("the pre-check is UX; the functional index is the law")
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void theIndexRejectsADuplicateBattleTag() {
        players.save(Player.register(new Username("someone"), new BattleTag("emilio#12345")));

        assertThatThrownBy(() -> players.save(Player.register(
                new Username("emilio"), new BattleTag("Emilio#12345"))))
                .as("one Battle.net account is one player")
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    /**
     * The whole point of WU17. Before this migration the owner id was a claim
     * the database could not check; now a character whose owner does not exist
     * is refused at the FK, even though the constraint is NOT VALID for the
     * rows that predate the players table.
     */
    @Test
    void aCharacterCannotBeSavedForAnOwnerThatIsNotAPlayer() {
        Character orphan = Character.register(new PlayerId(UUID.randomUUID()),
                new CharacterName("Frostbolt"), new Realm("Sanguino"), Region.EU,
                WowClass.MAGE, new GearSnapshot(new ItemLevel(675), OBSERVED_AT),
                Set.of(Specialization.MAGE_FROST),
                Map.of(Bracket.THREE_VS_THREE,
                        new PvpStanding(new Rating(2100), new PvpSeason(42), OBSERVED_AT)));

        assertThatThrownBy(() -> characters.save(orphan))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    private int countIn(String table) {
        return jdbc.queryForObject("select count(*) from " + table, Integer.class);
    }
}
