package com.pvpwow.lfg.application.service;

import com.pvpwow.lfg.domain.character.Specialization;
import com.pvpwow.lfg.domain.character.WowClass;

import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * The upstream's names, and ours.
 *
 * <p>Blizzard reports a class as {@code "Mage"} and a specialization as
 * {@code "Frost"}. We model them as {@code WowClass.MAGE} and {@code
 * Specialization.MAGE_FROST}. This class is where one becomes the other.
 *
 * <p>WHY THE NAME AND NOT THE ID: Blizzard also reports numeric ids (Mage is
 * 8, Frost is 63). The name is the robust key: the id space belongs to
 * Blizzard and can be renumbered between expansions, while the name is what a
 * human reads and what our enum spells out. A name we do not recognise is a
 * character we cannot model - and it is reported as such, not guessed at.
 *
 * <p>WHY THE SPEC NEEDS ITS CLASS: the specialization names REPEAT across
 * classes. "Frost" is Mage, Death Knight and Warlock; "Holy" is Priest and
 * Paladin. So a specialization is only "Frost" together with the class it
 * belongs to - which is exactly how our enum spells them: {@code MAGE_FROST},
 * {@code DEATH_KNIGHT_FROST}.
 *
 * <p>THE NAMES ARE BLIZZARD'S, NOT OURS: they are facts about the game, in
 * the spelling the game uses. They live here, in the application layer, and
 * not in the domain - the domain knows {@code MAGE_FROST}, not what Blizzard
 * calls it.
 */
final class BattleNetNames {

    /**
     * The thirteen playable classes, as Blizzard spells them.
     */
    private static final Map<String, WowClass> CLASSES = Map.ofEntries(
            Map.entry("death knight", WowClass.DEATH_KNIGHT),
            Map.entry("demon hunter", WowClass.DEMON_HUNTER),
            Map.entry("druid", WowClass.DRUID),
            Map.entry("evoker", WowClass.EVOKER),
            Map.entry("hunter", WowClass.HUNTER),
            Map.entry("mage", WowClass.MAGE),
            Map.entry("monk", WowClass.MONK),
            Map.entry("paladin", WowClass.PALADIN),
            Map.entry("priest", WowClass.PRIEST),
            Map.entry("rogue", WowClass.ROGUE),
            Map.entry("shaman", WowClass.SHAMAN),
            Map.entry("warlock", WowClass.WARLOCK),
            Map.entry("warrior", WowClass.WARRIOR));

    /**
     * The specializations, as Blizzard spells them, WITHOUT the class: the
     * pair (class, name) is what identifies one, because the names repeat.
     */
    private static final Map<String, String> SPECIALIZATIONS = Map.ofEntries(
            Map.entry("blood", "BLOOD"),
            Map.entry("frost", "FROST"),
            Map.entry("unholy", "UNHOLY"),
            Map.entry("havoc", "HAVOC"),
            Map.entry("vengeance", "VENGEANCE"),
            Map.entry("balance", "BALANCE"),
            Map.entry("feral", "FERAL"),
            Map.entry("guardian", "GUARDIAN"),
            Map.entry("restoration", "RESTORATION"),
            Map.entry("devastation", "DEVASTATION"),
            Map.entry("preservation", "PRESERVATION"),
            Map.entry("augmentation", "AUGMENTATION"),
            Map.entry("beast mastery", "BEAST_MASTERY"),
            Map.entry("marksmanship", "MARKSMANSHIP"),
            Map.entry("survival", "SURVIVAL"),
            Map.entry("arcane", "ARCANE"),
            Map.entry("fire", "FIRE"),
            Map.entry("brewmaster", "BREWMASTER"),
            Map.entry("mistweaver", "MISTWEAVER"),
            Map.entry("windwalker", "WINDWALKER"),
            Map.entry("holy", "HOLY"),
            Map.entry("protection", "PROTECTION"),
            Map.entry("retribution", "RETRIBUTION"),
            Map.entry("discipline", "DISCIPLINE"),
            Map.entry("shadow", "SHADOW"),
            Map.entry("assassination", "ASSASSINATION"),
            Map.entry("outlaw", "OUTLAW"),
            Map.entry("subtlety", "SUBTLETY"),
            Map.entry("elemental", "ELEMENTAL"),
            Map.entry("enhancement", "ENHANCEMENT"),
            Map.entry("affliction", "AFFLICTION"),
            Map.entry("demonology", "DEMONOLOGY"),
            Map.entry("destruction", "DESTRUCTION"),
            Map.entry("arms", "ARMS"),
            Map.entry("fury", "FURY"));

    private BattleNetNames() {
    }

    /**
     * The class Blizzard names, if we know it.
     */
    static Optional<WowClass> wowClass(String blizzardName) {
        if (blizzardName == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(CLASSES.get(normalize(blizzardName)));
    }

    /**
     * The specialization Blizzard names, if we know it AND it belongs to the
     * class - because the names repeat and only the pair identifies one.
     */
    static Optional<Specialization> specialization(String blizzardName, WowClass wowClass) {
        if (blizzardName == null) {
            return Optional.empty();
        }
        String suffix = SPECIALIZATIONS.get(normalize(blizzardName));
        if (suffix == null) {
            return Optional.empty();
        }
        try {
            return Optional.of(Specialization.valueOf(wowClass.name() + "_" + suffix));
        } catch (IllegalArgumentException notOurSpec) {
            // The name is a real specialization, but of another class. "Frost"
            // of a Warlock is not MAGE_FROST.
            return Optional.empty();
        }
    }

    /**
     * The spelling that matches the two vocabularies: lower case, with the
     * spaces Blizzard writes kept as spaces - because that is how Blizzard
     * writes them ("Beast Mastery", "Shadow" for the priest one).
     */
    private static String normalize(String name) {
        return name.trim().toLowerCase(Locale.ROOT);
    }
}
