package com.pvpwow.lfg.domain.character;

import com.pvpwow.lfg.domain.shared.DomainRuleViolationException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ItemLevelTest {

    @Test
    void acceptsMinimum() {
        assertThat(new ItemLevel(1).value()).isEqualTo(1);
    }

    @Test
    void acceptsTopOfTheLooseRange() {
        assertThat(new ItemLevel(1000).value()).isEqualTo(1000);
    }

    @Test
    void rejectsZero() {
        assertThatThrownBy(() -> new ItemLevel(0))
                .isInstanceOf(DomainRuleViolationException.class);
    }

    @Test
    void rejectsAboveMax() {
        assertThatThrownBy(() -> new ItemLevel(1001))
                .isInstanceOf(DomainRuleViolationException.class);
    }
}
