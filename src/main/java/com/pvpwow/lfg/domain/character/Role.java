package com.pvpwow.lfg.domain.character;

/**
 * The role a specialization performs in an arena comp.
 *
 * <p>Four values, not the classic tank/healer/damage trinity: the melee vs
 * ranged split is the first question players ask when building an arena comp
 * (melee cleave vs spell cleave).
 */
public enum Role {
    TANK,
    HEALER,
    MELEE_DPS,
    RANGED_DPS
}
