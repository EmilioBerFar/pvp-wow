package com.pvpwow.lfg.domain.synergy;

import com.pvpwow.lfg.domain.shared.DomainRuleViolationException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SynergyScoreTest {

    @Test
    void acceptsZero() {
        assertThat(new SynergyScore(0).value()).isZero();
    }

    @Test
    void acceptsOneHundred() {
        assertThat(new SynergyScore(100).value()).isEqualTo(100);
    }

    @Test
    void rejectsNegative() {
        assertThatThrownBy(() -> new SynergyScore(-1))
                .isInstanceOf(DomainRuleViolationException.class);
    }

    @Test
    void rejectsAboveOneHundred() {
        assertThatThrownBy(() -> new SynergyScore(101))
                .isInstanceOf(DomainRuleViolationException.class);
    }
}
