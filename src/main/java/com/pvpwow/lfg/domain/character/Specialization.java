package com.pvpwow.lfg.domain.character;

import java.util.Arrays;
import java.util.Set;

import static java.util.stream.Collectors.toUnmodifiableSet;

/**
 * One of the 39 playable specializations. Each carries the class it belongs
 * to and the role it performs in an arena comp.
 *
 * <p>Constants are prefixed by their class because names like FROST, HOLY or
 * PROTECTION repeat across classes. The pairs (class, spec) and (spec, role)
 * are facts of the game, not configuration.
 */
public enum Specialization {

    DEATH_KNIGHT_BLOOD(WowClass.DEATH_KNIGHT, Role.TANK),
    DEATH_KNIGHT_FROST(WowClass.DEATH_KNIGHT, Role.MELEE_DPS),
    DEATH_KNIGHT_UNHOLY(WowClass.DEATH_KNIGHT, Role.MELEE_DPS),

    DEMON_HUNTER_HAVOC(WowClass.DEMON_HUNTER, Role.MELEE_DPS),
    DEMON_HUNTER_VENGEANCE(WowClass.DEMON_HUNTER, Role.TANK),

    DRUID_BALANCE(WowClass.DRUID, Role.RANGED_DPS),
    DRUID_FERAL(WowClass.DRUID, Role.MELEE_DPS),
    DRUID_GUARDIAN(WowClass.DRUID, Role.TANK),
    DRUID_RESTORATION(WowClass.DRUID, Role.HEALER),

    EVOKER_DEVASTATION(WowClass.EVOKER, Role.RANGED_DPS),
    EVOKER_PRESERVATION(WowClass.EVOKER, Role.HEALER),
    EVOKER_AUGMENTATION(WowClass.EVOKER, Role.RANGED_DPS),

    HUNTER_BEAST_MASTERY(WowClass.HUNTER, Role.RANGED_DPS),
    HUNTER_MARKSMANSHIP(WowClass.HUNTER, Role.RANGED_DPS),
    HUNTER_SURVIVAL(WowClass.HUNTER, Role.MELEE_DPS),

    MAGE_ARCANE(WowClass.MAGE, Role.RANGED_DPS),
    MAGE_FIRE(WowClass.MAGE, Role.RANGED_DPS),
    MAGE_FROST(WowClass.MAGE, Role.RANGED_DPS),

    MONK_BREWMASTER(WowClass.MONK, Role.TANK),
    MONK_MISTWEAVER(WowClass.MONK, Role.HEALER),
    MONK_WINDWALKER(WowClass.MONK, Role.MELEE_DPS),

    PALADIN_HOLY(WowClass.PALADIN, Role.HEALER),
    PALADIN_PROTECTION(WowClass.PALADIN, Role.TANK),
    PALADIN_RETRIBUTION(WowClass.PALADIN, Role.MELEE_DPS),

    PRIEST_DISCIPLINE(WowClass.PRIEST, Role.HEALER),
    PRIEST_HOLY(WowClass.PRIEST, Role.HEALER),
    PRIEST_SHADOW(WowClass.PRIEST, Role.RANGED_DPS),

    ROGUE_ASSASSINATION(WowClass.ROGUE, Role.MELEE_DPS),
    ROGUE_OUTLAW(WowClass.ROGUE, Role.MELEE_DPS),
    ROGUE_SUBTLETY(WowClass.ROGUE, Role.MELEE_DPS),

    SHAMAN_ELEMENTAL(WowClass.SHAMAN, Role.RANGED_DPS),
    SHAMAN_ENHANCEMENT(WowClass.SHAMAN, Role.MELEE_DPS),
    SHAMAN_RESTORATION(WowClass.SHAMAN, Role.HEALER),

    WARLOCK_AFFLICTION(WowClass.WARLOCK, Role.RANGED_DPS),
    WARLOCK_DEMONOLOGY(WowClass.WARLOCK, Role.RANGED_DPS),
    WARLOCK_DESTRUCTION(WowClass.WARLOCK, Role.RANGED_DPS),

    WARRIOR_ARMS(WowClass.WARRIOR, Role.MELEE_DPS),
    WARRIOR_FURY(WowClass.WARRIOR, Role.MELEE_DPS),
    WARRIOR_PROTECTION(WowClass.WARRIOR, Role.TANK);

    private final WowClass wowClass;
    private final Role role;

    Specialization(WowClass wowClass, Role role) {
        this.wowClass = wowClass;
        this.role = role;
    }

    public WowClass wowClass() {
        return wowClass;
    }

    public Role role() {
        return role;
    }

    /**
     * All specializations a given class can play.
     */
    public static Set<Specialization> forClass(WowClass wowClass) {
        return Arrays.stream(values())
                .filter(spec -> spec.wowClass == wowClass)
                .collect(toUnmodifiableSet());
    }
}
