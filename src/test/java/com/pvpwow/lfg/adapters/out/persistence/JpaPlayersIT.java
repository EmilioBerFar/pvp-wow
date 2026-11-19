package com.pvpwow.lfg.adapters.out.persistence;

import com.pvpwow.lfg.application.port.out.Characters;
import com.pvpwow.lfg.application.port.out.Players;
import com.pvpwow.lfg.domain.character.BlizzardCharacterId;
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
import com.pvpwow.lfg.domain.player.BattleNetId;
import com.pvpwow.lfg.domain.player.BattleTag;
import com.pvpwow.lfg.domain.player.Player;
import com.pvpwow.lfg.domain.player.PlayerId;
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
 * The players table and the rules it carries, against the REAL database: one
 * Battle.net account is one player, a battle tag is NOT unique because
 * Blizzard reclaims them, a player cannot exist without an account, and a
 * character's owner must exist. An H2 imitation would pass and still lie about
 * a partial unique index and a NOT NULL column.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@Testcontainers
class JpaPlayersIT {
    /**
     * The Blizzard ids handed out in this test class. Each character needs its
     * own: the unique index is on (region, blizzard_character_id).
     */
    private static final java.util.concurrent.atomic.AtomicLong BLIZZARD_IDS =
            new java.util.concurrent.atomic.AtomicLong(161996209L);

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
    private static final BattleNetId ACCOUNT = new BattleNetId("123456789");

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
    void savesAndRehydratesTheLinkedAccount() {
        Player player = Player.register(ACCOUNT, new BattleTag("Emilio#12345"));

        players.save(player);

        Player rehydrated = players.findById(player.id()).orElseThrow();
        assertThat(rehydrated.id()).isEqualTo(player.id());
        assertThat(rehydrated.battleNetId()).isEqualTo(ACCOUNT);
        assertThat(rehydrated.battleTag()).isEqualTo(new BattleTag("emilio#12345"));
    }

    @Test
    void findsThePlayerByBattleNetId() {
        Player player = Player.register(ACCOUNT, new BattleTag("Emilio#12345"));
        players.save(player);

        assertThat(players.findByBattleNetId(ACCOUNT)).contains(player);
        assertThat(players.findByBattleNetId(new BattleNetId("999"))).isEmpty();
    }

    @Test
    void twoPlayersMayCarryTheSameBattleTag() {
        Player first = Player.register(ACCOUNT, new BattleTag("emilio#12345"));
        Player second = Player.register(new BattleNetId("999"), new BattleTag("emilio#12345"));

        players.save(first);
        players.save(second);

        assertThat(countIn("players")).isEqualTo(2);
    }

    @Test
    void theColumnRefusesAPlayerWithoutAnAccount() {
        assertThatThrownBy(() -> jdbc.update(
                "insert into players (id, battle_net_id, battle_tag) values (?, ?, ?)",
                UUID.randomUUID(), null, "emilio#12345"))
                .as("a player without a Battle.net account cannot exist")
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void savingTheSamePlayerTwiceUpdatesInsteadOfDuplicating() {
        Player player = Player.register(ACCOUNT, new BattleTag("emilio#12345"));

        players.save(player);
        players.save(player);

        assertThat(countIn("players")).isEqualTo(1);
    }

    @Test
    void theIndexRejectsASecondPlayerForTheSameBattleNetAccount() {
        players.save(Player.register(ACCOUNT, new BattleTag("emilio#12345")));

        assertThatThrownBy(() -> players.save(
                Player.register(ACCOUNT, new BattleTag("someoneelse#1234"))))
                .as("the login lookup is UX; the unique index is the law")
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    /**
     * The rule WU17 introduced and WU18 kept: a character whose owner does not
     * exist is refused at the foreign key, not by a service that might forget.
     */
    @Test
    void aCharacterCannotBeSavedForAnOwnerThatIsNotAPlayer() {
        Character orphan = Character.register(new PlayerId(UUID.randomUUID()),
                new BlizzardCharacterId(nextBlizzardId()), new CharacterName("Frostbolt"), new Realm("Sanguino"), Region.EU,
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

    /**
     * A Blizzard id nobody has used yet.
     *
     * <p>Every character needs its own: the unique index is on
     * (region, blizzard_character_id), and that index is the law of the
     * import - one Blizzard character, one row.
     */
    private static long nextBlizzardId() {
        return BLIZZARD_IDS.incrementAndGet();
    }
}
