package com.pvpwow.lfg.domain.character;

import com.pvpwow.lfg.domain.shared.DomainRuleViolationException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class BlizzardCharacterIdTest {

    @Test
    void itIsTheIdBlizzardSends() {
        assertThat(new BlizzardCharacterId(161996209L).value()).isEqualTo(161996209L);
    }

    @Test
    void aPositiveIdIsTheOnlyOneThatCanExist() {
        assertThatThrownBy(() -> new BlizzardCharacterId(0))
                .isInstanceOf(DomainRuleViolationException.class)
                .hasMessageContaining("positive");
        assertThatThrownBy(() -> new BlizzardCharacterId(-1))
                .isInstanceOf(DomainRuleViolationException.class);
    }

    @Test
    void twoIdsAreTheSameWhenTheyAreTheSameNumber() {
        // The identity of a character in the game, and a value object like any
        // other: the number IS the thing.
        assertThat(new BlizzardCharacterId(42L)).isEqualTo(new BlizzardCharacterId(42L));
        assertThat(new BlizzardCharacterId(42L)).isNotEqualTo(new BlizzardCharacterId(43L));
        assertThat(new BlizzardCharacterId(42L)).hasSameHashCodeAs(new BlizzardCharacterId(42L));
    }

    @Test
    void theFactoryReadsTheIdAsTheProviderSendsIt() {
        assertThat(BlizzardCharacterId.of(161996209L).value()).isEqualTo(161996209L);
    }
}
