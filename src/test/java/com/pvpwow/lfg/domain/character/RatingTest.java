package com.pvpwow.lfg.domain.character;

import com.pvpwow.lfg.domain.shared.DomainRuleViolationException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RatingTest {

    @Test
    void acceptsUnrankedFloor() {
        assertThat(new Rating(0).value()).isZero();
    }

    @Test
    void acceptsTopRating() {
        assertThat(new Rating(3500).value()).isEqualTo(3500);
    }

    @Test
    void rejectsNegative() {
        assertThatThrownBy(() -> new Rating(-1))
                .isInstanceOf(DomainRuleViolationException.class);
    }

    @Test
    void rejectsAboveMax() {
        assertThatThrownBy(() -> new Rating(3501))
                .isInstanceOf(DomainRuleViolationException.class);
    }
}
