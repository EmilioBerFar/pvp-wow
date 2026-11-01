package com.pvpwow.lfg.domain.character;

import com.pvpwow.lfg.domain.shared.DomainRuleViolationException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ItemLevelTest {

    @Test
    void acceptsTheSmallestPossibleItemLevel() {
        assertThat(new ItemLevel(1).value()).isEqualTo(1);
    }

    @Test
    void rejectsZeroAndNegativeBecauseTheyCannotExist() {
        assertThatThrownBy(() -> new ItemLevel(0))
                .isInstanceOf(DomainRuleViolationException.class);
        assertThatThrownBy(() -> new ItemLevel(-5))
                .isInstanceOf(DomainRuleViolationException.class);
    }

    @Test
    void acceptsValuesBeyondAnyCurrentCapBecauseCapsAreNotInvariants() {
        // Plausibility ("is 2000 believable this season?") is deliberately NOT
        // validated: caps move every season, and the observed truth will come
        // from the Blizzard API. The old 1..1000 range would have silently
        // rejected valid data the day a cap moved past it.
        assertThat(new ItemLevel(2000).value()).isEqualTo(2000);
    }
}
