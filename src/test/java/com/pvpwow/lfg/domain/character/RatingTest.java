package com.pvpwow.lfg.domain.character;

import com.pvpwow.lfg.domain.shared.DomainRuleViolationException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RatingTest {

    @Test
    void acceptsZeroBecauseAnUnrankedBracketIsARealState() {
        assertThat(new Rating(0).value()).isZero();
    }

    @Test
    void rejectsNegativeBecauseItCannotExist() {
        assertThatThrownBy(() -> new Rating(-1))
                .isInstanceOf(DomainRuleViolationException.class);
        assertThatThrownBy(() -> new Rating(-100))
                .isInstanceOf(DomainRuleViolationException.class);
    }

    @Test
    void acceptsValuesBeyondAnySeasonCapBecauseCapsAreNotInvariants() {
        // The old 0..3500 ceiling was a plausibility guess pretending to be an
        // invariant: cutoffs move, and the Blizzard API reports whatever the
        // game reports. If someone reintroduces a cap, this test forces the
        // conversation instead of failing silently in production.
        assertThat(new Rating(3501).value()).isEqualTo(3501);
    }
}
