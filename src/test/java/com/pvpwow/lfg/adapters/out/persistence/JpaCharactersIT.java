package com.pvpwow.lfg.adapters.out.persistence;

import com.pvpwow.lfg.application.port.out.Characters;
import com.pvpwow.lfg.domain.character.Bracket;
import com.pvpwow.lfg.domain.character.Character;
import com.pvpwow.lfg.domain.character.CharacterId;
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
 * The adapter against the REAL database: real dialect, real constraints, real
 * Flyway migration. An H2 in PostgreSQL mode would pass this suite and still
 * lie, because the thing under test is precisely the constraint that H2
 * imitates and Postgres enforces.
 *
 * <p>Runs as an IT (failsafe, {@code mvnw verify}) so {@code mvnw test} stays
 * Docker-free.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@Testcontainers
class JpaCharactersIT {

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
    private static final PlayerId PLAYER = new PlayerId(UUID.randomUUID());

    @Autowired
    private Characters characters;

    @Autowired
    private CharacterJpaRepository repository;

    @Autowired
    private JdbcTemplate jdbc;

    @AfterEach
    void emptyTheBoard() {
        repository.deleteAll();
    }

    @Test
    void savesTheAggregateAcrossTheRootAndItsChildTables() {
        Character saved = saved("Frostbolt");

        assertThat(countIn("characters")).isEqualTo(1);
        assertThat(countIn("character_specs")).isEqualTo(2);
        assertThat(countIn("character_standings")).isEqualTo(2);
        assertThat(jdbc.queryForObject(
                "select rating from character_standings where bracket = 'TWO_VS_TWO'",
                Integer.class)).isEqualTo(1800);
        assertThat(saved.id().value()).isNotNull();
    }

    @Test
    void rehydratesTheAggregateWithEveryFieldIntact() {
        Character saved = saved("Frostbolt");

        Character rehydrated = characters.findById(saved.id()).orElseThrow();

        assertThat(rehydrated.id()).isEqualTo(saved.id());
        assertThat(rehydrated.ownerId()).isEqualTo(PLAYER);
        assertThat(rehydrated.name()).isEqualTo(saved.name());
        assertThat(rehydrated.realm()).isEqualTo(new Realm("sanguino"));
        assertThat(rehydrated.gear()).isEqualTo(saved.gear());
        assertThat(rehydrated.specs()).containsExactlyInAnyOrder(
                Specialization.MAGE_FROST, Specialization.MAGE_ARCANE);
        assertThat(rehydrated.ratingFor(Bracket.THREE_VS_THREE))
                .contains(new Rating(2100));
    }

    @Test
    void findingAnUnknownIdReturnsEmpty() {
        assertThat(characters.findById(new CharacterId(UUID.randomUUID()))).isEmpty();
    }

    @Test
    void anUnrankedCharacterSavesWithNoStandingRows() {
        Character unranked = Character.register(PLAYER,
                new CharacterName("Tofuru"),
                new Realm("Illidan"),
                Region.US,
                WowClass.ROGUE,
                new GearSnapshot(new ItemLevel(600), OBSERVED_AT),
                Set.of(Specialization.ROGUE_SUBTLETY),
                Map.of());

        characters.save(unranked);

        assertThat(countIn("characters")).isEqualTo(1);
        assertThat(countIn("character_standings")).isZero();
    }

    @Test
    void savingTheSameAggregateTwiceUpdatesInsteadOfDuplicating() {
        Character saved = saved("Frostbolt");

        characters.save(saved);
        characters.save(saved);

        assertThat(countIn("characters")).isEqualTo(1);
        assertThat(countIn("character_specs")).isEqualTo(2);
        assertThat(countIn("character_standings")).isEqualTo(2);
    }

    @Test
    void alreadyRegisteredSeesThroughRealmPaddingAndNameCase() {
        saved("Frostbolt");

        assertThat(characters.alreadyRegistered(
                Region.EU, new Realm("  SANGUINO "), new CharacterName("frostbolt")))
                .isTrue();
        assertThat(characters.alreadyRegistered(
                Region.US, new Realm("Sanguino"), new CharacterName("Frostbolt")))
                .as("same realm name in another region is a different character")
                .isFalse();
    }

    @Test
    void theUniqueIndexIsTheLawEvenWhenTheAdvisoryPreCheckIsSkipped() {
        saved("Frostbolt");

        assertThatThrownBy(() -> characters.save(ranked("Frostbolt")))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void theUniqueIndexIsBlindToNameCasing() {
        saved("Frostbolt");

        assertThatThrownBy(() -> characters.save(ranked("frostbolt")))
                .as("WoW will not let you own the same toon twice by recasing it")
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    /**
     * Register AND persist. Every read test needs both, and forgetting the
     * save is exactly the mistake that made three of these tests lie once.
     */
    private Character saved(String name) {
        Character character = ranked(name);
        characters.save(character);
        return character;
    }

    private Character ranked(String name) {
        return Character.register(PLAYER,
                new CharacterName(name),
                new Realm("Sanguino"),
                Region.EU,
                WowClass.MAGE,
                new GearSnapshot(new ItemLevel(675), OBSERVED_AT),
                Set.of(Specialization.MAGE_FROST, Specialization.MAGE_ARCANE),
                Map.of(
                        Bracket.TWO_VS_TWO,
                        new PvpStanding(new Rating(1800), new PvpSeason(42), OBSERVED_AT),
                        Bracket.THREE_VS_THREE,
                        new PvpStanding(new Rating(2100), new PvpSeason(42), OBSERVED_AT)));
    }

    private int countIn(String table) {
        return jdbc.queryForObject("select count(*) from " + table, Integer.class);
    }
}
