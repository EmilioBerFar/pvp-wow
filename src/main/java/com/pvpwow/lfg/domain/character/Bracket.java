package com.pvpwow.lfg.domain.character;

/**
 * Competitive arena bracket.
 *
 * <p>Lives in the character package, not listing: ratings-per-bracket need it
 * here, and package dependencies flow listing → character, never the reverse.
 */
public enum Bracket {

    TWO_VS_TWO(2),
    THREE_VS_THREE(3);

    private final int teamSize;

    Bracket(int teamSize) {
        this.teamSize = teamSize;
    }

    public int teamSize() {
        return teamSize;
    }
}
