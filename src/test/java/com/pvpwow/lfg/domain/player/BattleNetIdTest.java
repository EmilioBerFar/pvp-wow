package com.pvpwow.lfg.domain.player;

import com.pvpwow.lfg.domain.shared.DomainRuleViolationException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class BattleNetIdTest {

    @Test
    void readsTheClaimAsTheProviderWritesIt() {
        assertThat(BattleNetId.parse("123456789").value()).isEqualTo("123456789");
        assertThat(BattleNetId.parse(" 42 ").value()).isEqualTo("42");
    }

    @Test
    void keepsTheClaimVerbatimBecauseTheProviderOwnsItsFormat() {
        // OpenID Connect defines `sub` as a string. Today's Battle.net sends
        // digits, but a column that only fits digits is a promise the provider
        // never made - and a broken one costs every account already stored.
        assertThat(BattleNetId.parse("abc-123").value()).isEqualTo("abc-123");
        assertThat(BattleNetId.parse("1234567890123456789012345").value())
                .isEqualTo("1234567890123456789012345");
    }

    @Test
    void rejectsABlankId() {
        assertThatThrownBy(() -> BattleNetId.parse(""))
                .isInstanceOf(DomainRuleViolationException.class)
                .hasMessageContaining("must not be blank");
        assertThatThrownBy(() -> BattleNetId.parse("   "))
                .isInstanceOf(DomainRuleViolationException.class)
                .hasMessageContaining("must not be blank");
    }

    @Test
    void rejectsANullId() {
        assertThatThrownBy(() -> BattleNetId.parse(null))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new BattleNetId(null))
                .isInstanceOf(NullPointerException.class);
    }
}
