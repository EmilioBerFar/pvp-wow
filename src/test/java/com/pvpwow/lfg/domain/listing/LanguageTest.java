package com.pvpwow.lfg.domain.listing;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class LanguageTest {

    @Test
    void everyLanguageCarriesItsIsoCode() {
        assertThat(Language.ES.isoCode()).isEqualTo("es");
        assertThat(Language.EN.isoCode()).isEqualTo("en");
        assertThat(Language.PT.isoCode()).isEqualTo("pt");
        assertThat(Language.FR.isoCode()).isEqualTo("fr");
        assertThat(Language.DE.isoCode()).isEqualTo("de");
        assertThat(Language.IT.isoCode()).isEqualTo("it");
    }

    @Test
    void hasSixEuLanguages() {
        assertThat(Language.values()).hasSize(6);
    }
}
