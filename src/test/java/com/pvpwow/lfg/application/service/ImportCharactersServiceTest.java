package com.pvpwow.lfg.application.service;

import com.pvpwow.lfg.application.port.in.ImportCharacters.CharacterOutcome;
import com.pvpwow.lfg.application.port.in.ImportCharacters.ChosenCharacter;
import com.pvpwow.lfg.application.port.in.ImportCharacters.ImportReport;
import com.pvpwow.lfg.application.port.in.ImportCharacters.Outcome;
import com.pvpwow.lfg.application.port.out.BattleNetCharacters;
import com.pvpwow.lfg.application.port.out.BattleNetCharacters.CharacterFacts;
import com.pvpwow.lfg.application.port.out.BattleNetCharacters.CharacterReference;
import com.pvpwow.lfg.application.port.out.Characters;
import com.pvpwow.lfg.domain.character.Bracket;
import com.pvpwow.lfg.domain.character.BlizzardCharacterId;
import com.pvpwow.lfg.domain.character.Character;
import com.pvpwow.lfg.domain.character.CharacterId;
import com.pvpwow.lfg.domain.character.CharacterName;
import com.pvpwow.lfg.domain.character.GearSnapshot;
import com.pvpwow.lfg.domain.character.ItemLevel;
import com.pvpwow.lfg.domain.character.PvpSeason;
import com.pvpwow.lfg.domain.character.Rating;
import com.pvpwow.lfg.domain.character.Realm;
import com.pvpwow.lfg.domain.character.Region;
import com.pvpwow.lfg.domain.character.Specialization;
import com.pvpwow.lfg.domain.character.WowClass;
import com.pvpwow.lfg.domain.player.PlayerId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The import, as the player experiences it: a batch where one refused
 * character does not throw away the others, and where the client cannot talk
 * the server into importing somebody else's character.
 */
class ImportCharactersServiceTest {

    private static final Instant NOW = Instant.parse("2026-11-19T10:00:00Z");
    private static final PlayerId OWNER = new PlayerId(UUID.randomUUID());
    private static final PlayerId SOMEONE_ELSE = new PlayerId(UUID.randomUUID());

    private static final long STRONGEM = 161996209L;
    private static final long GOODONE = 162000001L;
    private static final long LITTLEONE = 162000002L;
    private static final long FROM_THE_FUTURE = 162000003L;
    private static final long GHOST = 162000004L;

    private FakeBattleNet battleNet;
    private FakeCharacters characters;
    private ImportCharactersService service;

    @BeforeEach
    void setUp() {
        battleNet = new FakeBattleNet();
        characters = new FakeCharacters();
        service = new ImportCharactersService(battleNet, characters,
                () -> OWNER, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void aChosenCharacterIsImportedWithTheFactsBlizzardReports() {
        inTheAccount(STRONGEM, "Strongem", "Mage", 70);
        battleNet.facts("Strongem", "Mage", "Frost", 702, Map.of(
                Bracket.TWO_VS_TWO, new BattleNetCharacters.Rating(1800, new PvpSeason(43))));

        ImportReport report = service.importChosen(Region.EU, List.of(chosen(STRONGEM)));

        assertThat(report.imported()).isEqualTo(1);
        assertThat(report.outcomes()).singleElement().satisfies(outcome -> {
            assertThat(outcome.outcome()).isEqualTo(Outcome.IMPORTED);
            assertThat(outcome.name()).isEqualTo("Strongem");
        });

        Character imported = characters.saved.get(0);
        assertThat(imported.name().value()).isEqualTo("Strongem");
        assertThat(imported.realm().value()).isEqualTo("tarren-mill");
        assertThat(imported.region()).isEqualTo(Region.EU);
        assertThat(imported.wowClass()).isEqualTo(WowClass.MAGE);
        assertThat(imported.itemLevel().value()).isEqualTo(702);
        assertThat(imported.specs()).containsExactly(Specialization.MAGE_FROST);
        assertThat(imported.ratingFor(Bracket.TWO_VS_TWO)).contains(new Rating(1800));
        assertThat(imported.standingFor(Bracket.TWO_VS_TWO).orElseThrow().season())
                .isEqualTo(new PvpSeason(43));
    }

    @Test
    void theRatingCarriesTheSeasonBlizzardReportedAndNotTheConfiguredOne() {
        // The upstream knows which season it is talking about. A rating filed
        // under the wrong season is a different fact about the character.
        inTheAccount(STRONGEM, "Strongem", "Mage", 70);
        battleNet.facts("Strongem", "Mage", "Frost", 702, Map.of(
                Bracket.TWO_VS_TWO, new BattleNetCharacters.Rating(1800, new PvpSeason(43))));

        service.importChosen(Region.EU, List.of(chosen(STRONGEM)));

        assertThat(characters.saved.get(0).standingFor(Bracket.TWO_VS_TWO).orElseThrow().season())
                .isEqualTo(new PvpSeason(43));
    }

    // ---- the gate: the account list decides what may be imported ----

    @Test
    void aCharacterThatIsNotInTheAccountIsRejected() {
        // THE CASE: the Blizzard character endpoint is PUBLIC - it answers
        // for any character in the game. So the client cannot be allowed to
        // name a character: only the ACCOUNT LIST may. An id that is not in
        // it is not importable, and the message says nothing about who does
        // own it.
        inTheAccount(STRONGEM, "Strongem", "Mage", 70);
        battleNet.facts("SomebodyElses", "Mage", "Frost", 702, Map.of());

        ImportReport report = service.importChosen(Region.EU, List.of(chosen(GHOST)));

        assertThat(report.rejected()).isEqualTo(1);
        assertThat(report.imported()).isZero();
        assertThat(characters.saved).isEmpty();
        assertThat(report.outcomes()).singleElement().satisfies(outcome -> {
            assertThat(outcome.outcome()).isEqualTo(Outcome.REJECTED);
            assertThat(outcome.reason()).contains("does not belong to your account");
            // The message must NOT leak anything about the real owner.
            assertThat(outcome.reason()).doesNotContain("somebody");
        });
    }

    @Test
    void aCharacterOutsideTheAccountIsNeverEvenAskedBlizzardAboutIt() {
        // The gate is not just about the answer: a character that is not in
        // the account must not reach the upstream at all.
        inTheAccount(STRONGEM, "Strongem", "Mage", 70);

        service.importChosen(Region.EU, List.of(chosen(GHOST)));

        assertThat(battleNet.askedAbout).isEmpty();
    }

    @Test
    void aCharacterOfAnotherPlayerIsRejectedWithoutSayingWhoHasIt() {
        // Blizzard says the character is in THIS account now; our database
        // says another player has it. That means it was transferred between
        // Battle.net accounts. We do not transfer it, and we say NOTHING
        // about who has it - the same anti-enumeration rule as the 404.
        inTheAccount(STRONGEM, "Thrall", "Mage", 70);
        battleNet.facts("Thrall", "Mage", "Frost", 702, Map.of());
        characters.existing = character("Thrall", SOMEONE_ELSE, 600);

        ImportReport report = service.importChosen(Region.EU, List.of(chosen(STRONGEM)));

        assertThat(report.rejected()).isEqualTo(1);
        assertThat(report.imported()).isZero();
        assertThat(report.refreshed()).isZero();
        assertThat(characters.saved).isEmpty();
        assertThat(report.outcomes()).singleElement().satisfies(outcome -> {
            assertThat(outcome.reason()).contains("cannot be imported");
            assertThat(outcome.reason()).doesNotContain("another player");
        });
    }

    @Test
    void aCharacterTheDomainRefusesDoesNotAbortTheImport() {
        // THE CASE: a character the domain will not accept - here one whose
        // specialization belongs to another class - is refused, and the
        // others are imported anyway. A batch that died because of one
        // member would throw away work the player asked for.
        inTheAccount(GOODONE, "GoodOne", "Mage", 70);
        inTheAccount(LITTLEONE, "WrongSpec", "Mage", 70);
        inTheAccount(FROM_THE_FUTURE, "AlsoGood", "Priest", 70);
        battleNet.facts("GoodOne", "Mage", "Frost", 702, Map.of());
        battleNet.facts("WrongSpec", "Mage", "Shadow", 700, Map.of());
        battleNet.facts("AlsoGood", "Priest", "Holy", 690, Map.of());

        ImportReport report = service.importChosen(Region.EU, List.of(
                chosen(GOODONE), chosen(LITTLEONE), chosen(FROM_THE_FUTURE)));

        assertThat(report.imported()).isEqualTo(2);
        assertThat(report.rejected()).isEqualTo(1);
        assertThat(characters.saved).hasSize(2);
        assertThat(characters.saved).extracting(character -> character.name().value())
                .containsExactly("GoodOne", "AlsoGood");
    }

    @Test
    void aRefusedCharacterSaysWhy() {
        inTheAccount(LITTLEONE, "WrongSpec", "Mage", 70);
        battleNet.facts("WrongSpec", "Mage", "Shadow", 700, Map.of());

        ImportReport report = service.importChosen(Region.EU, List.of(chosen(LITTLEONE)));

        assertThat(report.outcomes()).singleElement().satisfies(outcome -> {
            assertThat(outcome.outcome()).isEqualTo(Outcome.REJECTED);
            assertThat(outcome.reason()).contains("Shadow").contains("Mage");
        });
    }

    @Test
    void aCharacterWithNoSpecializationIsRefusedWithItsReason() {
        // Blizzard omits active_spec for characters below level 10.
        inTheAccount(LITTLEONE, "LittleOne", "Mage", 8);
        battleNet.factsWithoutSpecialization("LittleOne", "Mage", 120, Map.of());

        ImportReport report = service.importChosen(Region.EU, List.of(chosen(LITTLEONE)));

        assertThat(report.rejected()).isEqualTo(1);
        assertThat(report.outcomes()).singleElement().satisfies(outcome ->
                assertThat(outcome.reason()).contains("no specialization"));
    }

    @Test
    void aClassWeDoNotKnowIsRefusedWithItsReason() {
        inTheAccount(FROM_THE_FUTURE, "FromBeyond", "Necromancer", 70);
        battleNet.facts("FromBeyond", "Necromancer", "Bone", 700, Map.of());

        ImportReport report = service.importChosen(Region.EU, List.of(chosen(FROM_THE_FUTURE)));

        assertThat(report.rejected()).isEqualTo(1);
        assertThat(report.outcomes()).singleElement().satisfies(outcome ->
                assertThat(outcome.reason()).contains("unknown class").contains("Necromancer"));
    }

    @Test
    void ourOwnCharacterIsRefreshedAndItsSpecsAreLeftAlone() {
        // The specs are the player's: they declared more than one, and a
        // refresh does not take that away.
        inTheAccount(STRONGEM, "Strongem", "Mage", 70);
        Character mine = character("Strongem", OWNER, 600);
        mine = Character.rehydrate(mine.id(), OWNER, new BlizzardCharacterId(STRONGEM),
                mine.name(), mine.realm(), mine.region(),
                mine.wowClass(), mine.gear(),
                Set.of(Specialization.MAGE_FROST, Specialization.MAGE_ARCANE), Map.of());
        characters.existing = mine;
        battleNet.facts("Strongem", "Mage", "Frost", 702, Map.of(
                Bracket.TWO_VS_TWO, new BattleNetCharacters.Rating(1900, new PvpSeason(43))));

        ImportReport report = service.importChosen(Region.EU, List.of(chosen(STRONGEM)));

        assertThat(report.refreshed()).isEqualTo(1);
        assertThat(report.imported()).isZero();
        Character refreshed = characters.saved.get(0);
        assertThat(refreshed.itemLevel().value()).isEqualTo(702);
        assertThat(refreshed.ratingFor(Bracket.TWO_VS_TWO)).contains(new Rating(1900));
        assertThat(refreshed.specs()).containsExactlyInAnyOrder(
                Specialization.MAGE_FROST, Specialization.MAGE_ARCANE);
    }

    @Test
    void aCharacterBlizzardDoesNotKnowIsSkipped() {
        // In the account, but the upstream does not answer for it: it was
        // deleted, or renamed.
        inTheAccount(GHOST, "Ghost", "Mage", 70);

        ImportReport report = service.importChosen(Region.EU, List.of(chosen(GHOST)));

        assertThat(report.skipped()).isEqualTo(1);
        assertThat(characters.saved).isEmpty();
    }

    @Test
    void everyOutcomeIsReportedForEveryChosenCharacter() {
        inTheAccount(GOODONE, "GoodOne", "Mage", 70);
        inTheAccount(FROM_THE_FUTURE, "FromBeyond", "Necromancer", 70);
        inTheAccount(GHOST, "Ghost", "Mage", 70);
        battleNet.facts("GoodOne", "Mage", "Frost", 702, Map.of());
        battleNet.facts("FromBeyond", "Necromancer", "Bone", 700, Map.of());

        ImportReport report = service.importChosen(Region.EU, List.of(
                chosen(GOODONE), chosen(FROM_THE_FUTURE), chosen(GHOST)));

        assertThat(report.outcomes()).hasSize(3);
        assertThat(report.outcomes()).extracting(CharacterOutcome::outcome)
                .containsExactlyInAnyOrder(Outcome.IMPORTED, Outcome.REJECTED, Outcome.SKIPPED);
    }

    // ---- the listing ----

    @Test
    void theListingShowsTheCharactersOfMaximumLevelAndNoOthers() {
        // The maximum is the one AMONG THE ACCOUNT'S OWN CHARACTERS, so it
        // needs no number that goes stale with an expansion.
        battleNet.addToAccount(STRONGEM, "Strongem", "tarren-mill", "Mage", 70);
        battleNet.addToAccount(162000010L, "OldOne", "tarren-mill", "Mage", 20);

        List<com.pvpwow.lfg.application.port.in.ImportCharacters.ImportableCharacter> listing =
                service.listImportable(Region.EU);

        assertThat(listing).singleElement().satisfies(character -> {
            assertThat(character.blizzardId()).isEqualTo(STRONGEM);
            assertThat(character.name()).isEqualTo("Strongem");
            assertThat(character.realmSlug()).isEqualTo("tarren-mill");
            assertThat(character.className()).isEqualTo("Mage");
            assertThat(character.level()).isEqualTo(70);
        });
    }

    @Test
    void theListingCostsOneCallAndWritesNothing() {
        inTheAccount(STRONGEM, "Strongem", "Mage", 70);

        service.listImportable(Region.EU);

        assertThat(characters.saved).isEmpty();
    }

    private ChosenCharacter chosen(long blizzardId) {
        return new ChosenCharacter(blizzardId);
    }

    /**
     * Puts a character in the account, the way the upstream lists it.
     */
    private void inTheAccount(long blizzardId, String name, String className, int level) {
        battleNet.addToAccount(blizzardId, name, "tarren-mill", className, level);
    }

    private Character character(String name, PlayerId owner, int itemLevel) {
        return Character.register(owner, new BlizzardCharacterId(STRONGEM),
                new CharacterName(name), new Realm("tarren-mill"),
                Region.EU, WowClass.MAGE,
                new GearSnapshot(new ItemLevel(itemLevel), NOW),
                Set.of(Specialization.MAGE_FROST), Map.of());
    }

    /**
     * A Blizzard that answers what the test tells it to.
     */
    private static final class FakeBattleNet implements BattleNetCharacters {

        private final Map<String, CharacterFacts> facts = new java.util.HashMap<>();
        private final List<BattleNetCharacters.AccountCharacter> characters = new ArrayList<>();
        private final List<String> askedAbout = new ArrayList<>();

        void addToAccount(long blizzardId, String name, String realmSlug,
                          String className, int level) {
            characters.add(new BattleNetCharacters.AccountCharacter(
                    blizzardId, name, realmSlug, className, level));
        }

        void facts(String name, String className, String specialization, int itemLevel,
                  Map<Bracket, BattleNetCharacters.Rating> ratings) {
            facts.put(name, new CharacterFacts(className, specialization, itemLevel, ratings));
        }

        void factsWithoutSpecialization(String name, String className, int itemLevel,
                                        Map<Bracket, BattleNetCharacters.Rating> ratings) {
            facts.put(name, new CharacterFacts(className, null, itemLevel, ratings));
        }

        @Override
        public List<BattleNetCharacters.AccountCharacter> charactersIn(Region region) {
            return List.copyOf(characters);
        }

        @Override
        public Optional<CharacterFacts> factsOf(CharacterReference character) {
            askedAbout.add(character.name());
            return Optional.ofNullable(facts.get(character.name()));
        }
    }

    /**
     * A database that remembers what it was told.
     */
    private static final class FakeCharacters implements Characters {

        private final List<Character> saved = new ArrayList<>();
        private Character existing;

        @Override
        public void save(Character character) {
            saved.add(character);
        }

        @Override
        public Optional<Character> findById(CharacterId id) {
            return Optional.empty();
        }

        @Override
        public Optional<Character> findByBlizzardId(Region region,
                                                     BlizzardCharacterId blizzardId) {
            return Optional.ofNullable(existing);
        }
    }
}
