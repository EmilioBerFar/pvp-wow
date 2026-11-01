package com.pvpwow.lfg.domain.player;

import com.pvpwow.lfg.domain.shared.DomainRuleViolationException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class UsernameTest {

    @Test
    void trimsSurroundingWhitespace() {
        assertThat(new Username("  emilio  ").value()).isEqualTo("emilio");
    }

    @Test
    void acceptsMinimumLength() {
        assertThatNoException().isThrownBy(() -> new Username("abc"));
    }

    @Test
    void acceptsMaximumLength() {
        assertThatNoException().isThrownBy(() -> new Username("a".repeat(24)));
    }

    @Test
    void acceptsDotsUnderscoresAndHyphensAfterTheFirstLetter() {
        assertThat(new Username("emilio.ber_far-01").value())
                .isEqualTo("emilio.ber_far-01");
    }

    @Test
    void rejectsTooShort() {
        assertThatThrownBy(() -> new Username("ab"))
                .isInstanceOf(DomainRuleViolationException.class);
    }

    @Test
    void rejectsTooLong() {
        assertThatThrownBy(() -> new Username("a".repeat(25)))
                .isInstanceOf(DomainRuleViolationException.class);
    }

    @Test
    void rejectsNamesNotStartingWithALetter() {
        assertThatThrownBy(() -> new Username("1emilio"))
                .isInstanceOf(DomainRuleViolationException.class);
        assertThatThrownBy(() -> new Username("_emilio"))
                .isInstanceOf(DomainRuleViolationException.class);
        assertThatThrownBy(() -> new Username(".emilio"))
                .isInstanceOf(DomainRuleViolationException.class);
        assertThatThrownBy(() -> new Username("-emilio"))
                .isInstanceOf(DomainRuleViolationException.class);
    }

    @Test
    void rejectsIllegalCharacters() {
        assertThatThrownBy(() -> new Username("emilio ber"))
                .isInstanceOf(DomainRuleViolationException.class);
        assertThatThrownBy(() -> new Username("emilio@home"))
                .isInstanceOf(DomainRuleViolationException.class);
    }

    @Test
    void rejectsBlank() {
        assertThatThrownBy(() -> new Username("   "))
                .isInstanceOf(DomainRuleViolationException.class);
    }

    @Test
    void rejectsNull() {
        assertThatThrownBy(() -> new Username(null))
                .isInstanceOf(NullPointerException.class);
    }
}
