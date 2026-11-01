package com.pvpwow.lfg.domain.listing;

/**
 * Language a player is willing to speak on voice comms.
 *
 * <p>EU-centric set in v1; extendable. The ISO 639-1 code is what adapters
 * persist and what the frontend translates — never the constant name.
 */
public enum Language {

    ES("es"),
    EN("en"),
    PT("pt"),
    FR("fr"),
    DE("de"),
    IT("it");

    private final String isoCode;

    Language(String isoCode) {
        this.isoCode = isoCode;
    }

    public String isoCode() {
        return isoCode;
    }
}
