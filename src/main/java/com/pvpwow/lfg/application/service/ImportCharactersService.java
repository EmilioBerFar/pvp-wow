package com.pvpwow.lfg.application.service;

import com.pvpwow.lfg.application.port.in.ImportCharacters;
import com.pvpwow.lfg.application.port.out.BattleNetCharacters;
import com.pvpwow.lfg.application.port.out.BattleNetCharacters.CharacterFacts;
import com.pvpwow.lfg.application.port.out.BattleNetCharacters.CharacterReference;
import com.pvpwow.lfg.application.port.out.Characters;
import com.pvpwow.lfg.application.port.out.CurrentPlayerProvider;
import com.pvpwow.lfg.domain.character.Bracket;
import com.pvpwow.lfg.domain.character.BlizzardCharacterId;
import com.pvpwow.lfg.domain.character.Character;
import com.pvpwow.lfg.domain.character.CharacterId;
import com.pvpwow.lfg.domain.character.CharacterName;
import com.pvpwow.lfg.domain.character.GearSnapshot;
import com.pvpwow.lfg.domain.character.ItemLevel;
import com.pvpwow.lfg.domain.character.PvpStanding;
import com.pvpwow.lfg.domain.character.PvpSeason;
import com.pvpwow.lfg.domain.character.Rating;
import com.pvpwow.lfg.domain.character.Realm;
import com.pvpwow.lfg.domain.character.Region;
import com.pvpwow.lfg.domain.character.Specialization;
import com.pvpwow.lfg.domain.character.WowClass;
import com.pvpwow.lfg.domain.player.PlayerId;
import com.pvpwow.lfg.domain.shared.DomainRuleViolationException;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * The import: characters the player chose, and the facts Blizzard reports
 * about them.
 *
 * <p>WHAT THIS USE CASE DECIDES, AND WHAT IT DOES NOT: it decides what a
 * Blizzard report MEANS for a character - that is the whole point of the
 * declared/observed seam. It does not decide whether a character is valid:
 * that is the domain's, and the domain answers by refusing to build the
 * object. A refusal here is not an error to be thrown and forgotten, it is a
 * fact about one character in a batch.
 *
 * <p>WHY ONE REFUSED CHARACTER DOES NOT ABORT THE IMPORT: the player chose
 * five characters, and one of them is a level 8 toon with no specialization,
 * or a character another player already owns. The other four are fine, and
 * the player asked for them. A batch that dies because of one member throws
 * away work that was wanted. So the result carries one outcome per character,
 * and the REJECTED ones carry the reason.
 *
 * <p>THE DECLARED FACTS ARE THE PLAYER'S: a character is created with the
 * active specialization Blizzard reports, because that is the only
 * specialization the player could have declared without typing it. But it is
 * a STARTING POINT, not a verdict: the specs of a character are what the
 * player is willing to play, and a player who wants to queue as Frost AND
 * Arcane says so afterwards. Refreshing a character never touches them - the
 * domain's {@code observe} door does not touch the declared facts, and this
 * use case inherits that.
 */
public final class ImportCharactersService implements ImportCharacters {

    private final BattleNetCharacters battleNet;
    private final Characters characters;
    private final CurrentPlayerProvider currentPlayer;
    private final Clock clock;

    public ImportCharactersService(BattleNetCharacters battleNet,
                                   Characters characters,
                                   CurrentPlayerProvider currentPlayer,
                                   Clock clock) {
        this.battleNet = Objects.requireNonNull(battleNet, "battleNet must not be null");
        this.characters = Objects.requireNonNull(characters, "characters must not be null");
        this.currentPlayer = Objects.requireNonNull(currentPlayer, "currentPlayer must not be null");
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
    }

    @Override
    public List<ImportableCharacter> listImportable(Region region) {
        List<BattleNetCharacters.AccountCharacter> characters = battleNet.charactersIn(region);

        // THE LIST SHOWS THE CHARACTERS OF MAXIMUM LEVEL. The level maximum
        // is the one AMONG THE ACCOUNT'S OWN CHARACTERS, so it needs no
        // number that can go stale with an expansion - and the levels of a
        // character do not change often enough to matter for a list.
        //
        // The cost of this rule, stated because it is real: an account whose
        // only character is below the maximum sees an EMPTY list. That is the
        // price of "no hard-coded cap", and the alternative - a fixed 70 -
        // would have to be changed every expansion.
        int maximum = characters.stream()
                .mapToInt(BattleNetCharacters.AccountCharacter::level)
                .max()
                .orElse(0);

        return characters.stream()
                .filter(character -> character.level() == maximum)
                .map(character -> new ImportableCharacter(
                        character.blizzardId(),
                        character.name(),
                        character.realmSlug(),
                        character.className(),
                        character.level()))
                .toList();
    }

    @Override
    public ImportReport importChosen(Region region, List<ChosenCharacter> chosen) {
        // THE ACCOUNT LIST IS THE GATE. It is fetched with the token of the
        // signed-in player, so it answers with THEIR characters and nobody
        // else's. A character that is not in this list is not importable -
        // the client cannot talk the server into importing somebody else's
        // character, because the character endpoint of the Blizzard API is
        // public and would happily answer for it.
        //
        // The list is fetched ONCE for the whole batch: it is one call, and
        // every choice is checked against the same snapshot of the account.
        Map<Long, BattleNetCharacters.AccountCharacter> account = accountOf(region);

        List<CharacterOutcome> outcomes = new ArrayList<>();
        for (ChosenCharacter selection : chosen) {
            outcomes.add(importOne(selection, region, account));
        }
        return new ImportReport(outcomes);
    }

    /**
     * The characters of the signed-in player's account, by id.
     */
    private Map<Long, BattleNetCharacters.AccountCharacter> accountOf(Region region) {
        Map<Long, BattleNetCharacters.AccountCharacter> account = new LinkedHashMap<>();
        for (BattleNetCharacters.AccountCharacter character : battleNet.charactersIn(region)) {
            account.put(character.blizzardId(), character);
        }
        return account;
    }

    /**
     * One character, and whatever happens to it.
     */
    private CharacterOutcome importOne(ChosenCharacter selection,
                                       Region region,
                                       Map<Long, BattleNetCharacters.AccountCharacter> account) {
        BattleNetCharacters.AccountCharacter inAccount = account.get(selection.blizzardId());
        if (inAccount == null) {
            // It is not in the account of the signed-in player. The message
            // says NOTHING about who does own it: this is the same
            // anti-enumeration rule as the 404 of a character that belongs to
            // another player.
            return CharacterOutcome.rejected("", "",
                    "this character cannot be imported: it does not belong to your account");
        }

        // The name and the realm are validated BEFORE the call to Blizzard:
        // a name the domain would refuse is not worth a round trip, and the
        // path of the upstream is built from them.
        Realm realm;
        CharacterName name;
        try {
            realm = new Realm(inAccount.realmSlug());
            name = new CharacterName(inAccount.name());
        } catch (DomainRuleViolationException refused) {
            return CharacterOutcome.rejected(inAccount.name(), inAccount.realmSlug(),
                    refused.getMessage());
        }

        // The client sends an id and NOTHING ELSE, so the name and the realm
        // here come from the ACCOUNT LIST - the one endpoint that answers
        // with the token of the signed-in player, and therefore with THEIR
        // characters. That is the whole point of the gate above.
        CharacterReference reference = new CharacterReference(
                inAccount.blizzardId(), name.value(), realm.value(), region);

        Optional<CharacterFacts> facts = battleNet.factsOf(reference);
        if (facts.isEmpty()) {
            // Blizzard does not know this character - it was deleted, or it
            // was renamed and the name we hold is stale. There is nothing to
            // import, and that is not a failure of the import.
            return CharacterOutcome.skipped(inAccount.name(), inAccount.realmSlug(),
                    "Blizzard does not know this character any more");
        }

        CharacterFacts observed = facts.get();

        // The class and the specialization are TRANSLATED here, and a name we
        // do not know is a character we cannot model. It is refused with the
        // reason, and the batch carries on.
        Optional<WowClass> wowClass = BattleNetNames.wowClass(observed.className());
        if (wowClass.isEmpty()) {
            return CharacterOutcome.rejected(inAccount.name(), inAccount.realmSlug(),
                    "unknown class: " + observed.className());
        }
        Optional<Specialization> specialization =
                BattleNetNames.specialization(observed.specializationName(), wowClass.get());
        if (specialization.isEmpty()) {
            return CharacterOutcome.rejected(inAccount.name(), inAccount.realmSlug(),
                    reasonForMissingSpecialization(observed));
        }

        return save(inAccount, region, name, realm, wowClass.get(), specialization.get(), observed);
    }

    /**
     * The character, built by the domain or refused by it.
     */
    private CharacterOutcome save(BattleNetCharacters.AccountCharacter inAccount,
                                  Region region,
                                  CharacterName name,
                                  Realm realm,
                                  WowClass wowClass,
                                  Specialization specialization,
                                  CharacterFacts facts) {
        PlayerId owner = currentPlayer.currentPlayer();
        Instant now = clock.instant();
        BlizzardCharacterId blizzardId = new BlizzardCharacterId(inAccount.blizzardId());

        // The character is recognised by the id the provider gave it, not by
        // its name: a renamed character is still the same character, and the
        // natural key would not find it.
        Optional<Character> existing = characters.findByBlizzardId(region, blizzardId);
        if (existing.isPresent()) {
            Character character = existing.get();
            if (!character.ownerId().equals(owner)) {
                // It belongs to somebody else in our database, even though
                // Blizzard says it is in THIS account now. The character was
                // transferred between Battle.net accounts, and we do not
                // transfer it: the message says NOTHING about who has it, for
                // the same reason a 404 says nothing.
                //
                // The consequence, on purpose: a character transferred
                // between accounts is stuck. It is a conscious limitation,
                // and the spec says so.
                return CharacterOutcome.rejected(inAccount.name(), inAccount.realmSlug(),
                        "this character cannot be imported");
            }
            // Ours: refresh the observed facts and leave the declared ones
            // alone. The domain's observe() door is where that rule lives.
            Character refreshed = character.observe(
                    new GearSnapshot(new ItemLevel(facts.equippedItemLevel()), now),
                    standingsOf(facts, now));
            characters.save(refreshed);
            return CharacterOutcome.refreshed(inAccount.name(), inAccount.realmSlug());
        }

        try {
            Character created = Character.register(
                    owner, blizzardId, name, realm, region, wowClass,
                    new GearSnapshot(new ItemLevel(facts.equippedItemLevel()), now),
                    Set.of(specialization),
                    standingsOf(facts, now));
            characters.save(created);
            return CharacterOutcome.imported(inAccount.name(), inAccount.realmSlug());
        } catch (DomainRuleViolationException refused) {
            // The domain says no, and it says why. That is the answer for
            // this character, and the others carry on.
            return CharacterOutcome.rejected(inAccount.name(), inAccount.realmSlug(),
                    refused.getMessage());
        }
    }

    /**
     * The ratings, as the domain models them.
     *
     * <p>Each rating carries the season the upstream reported with it: a
     * rating without a season is meaningless, and the upstream knows which
     * season it is talking about.
     */
    private Map<Bracket, PvpStanding> standingsOf(CharacterFacts facts, Instant observedAt) {
        Map<Bracket, PvpStanding> standings = new LinkedHashMap<>();
        facts.ratings().forEach((bracket, rating) -> standings.put(bracket,
                new PvpStanding(new Rating(rating.value()), rating.season(), observedAt)));
        return standings;
    }

    /**
     * Why a character was refused, in the words of whoever refused it.
     */
    private String reasonForMissingSpecialization(CharacterFacts facts) {
        if (facts.specializationName() == null || facts.specializationName().isBlank()) {
            return "Blizzard reports no specialization for this character";
        }
        return "unknown specialization for class "
                + facts.className() + ": " + facts.specializationName();
    }
}
