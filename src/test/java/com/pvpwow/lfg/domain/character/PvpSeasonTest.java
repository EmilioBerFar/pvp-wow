package com.pvpwow.lfg.domain.character;

import com.pvpwow.lfg.domain.shared.DomainRuleViolationException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PvpSeasonTest {

    @Test
    void keepsBlizzardsSeasonId() {
        assertThat(new PvpSeason(42).id()).isEqualTo(42);
    }

    @Test
    void rejectsNonPositiveIds() {
        assertThatThrownBy(() -> new PvpSeason(0))
                .isInstanceOf(DomainRuleViolationException.class)
                .hasMessageContaining("positive");
        assertThatThrownBy(() -> new PvpSeason(-1))
                .isInstanceOf(DomainRuleViolationException.class);
    }
}
