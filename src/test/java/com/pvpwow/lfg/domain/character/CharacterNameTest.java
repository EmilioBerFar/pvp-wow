package com.pvpwow.lfg.domain.character;

import com.pvpwow.lfg.domain.shared.DomainRuleViolationException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Reference standard for testing value objects: one happy path, every boundary,
 * and one test per category of invalid input. No mocks — the domain is pure.
 */
class CharacterNameTest {

    @Test
    void acceptsPlainName() {
        assertThat(new CharacterName("Thrall").value()).isEqualTo("Thrall");
    }

    @Test
    void acceptsAccentedLetters() {
        assertThat(new CharacterName("Maëlle").value()).isEqualTo("Maëlle");
    }

    @Test
    void acceptsMinimumLengthOfTwoLetters() {
        assertThat(new CharacterName("Xi").value()).isEqualTo("Xi");
    }

    @Test
    void acceptsMaximumLengthOfTwelveLetters() {
        assertThat(new CharacterName("abcdefghijkl").value()).isEqualTo("abcdefghijkl");
    }

    @Test
    void trimsSurroundingWhitespace() {
        assertThat(new CharacterName("  Thrall  ").value()).isEqualTo("Thrall");
    }

    @Test
    void rejectsOneLetter() {
        assertThatThrownBy(() -> new CharacterName("X"))
                .isInstanceOf(DomainRuleViolationException.class);
    }

    @Test
    void rejectsThirteenLetters() {
        assertThatThrownBy(() -> new CharacterName("abcdefghijklm"))
                .isInstanceOf(DomainRuleViolationException.class);
    }

    @Test
    void rejectsDigits() {
        assertThatThrownBy(() -> new CharacterName("Thrall1"))
                .isInstanceOf(DomainRuleViolationException.class);
    }

    @Test
    void rejectsSpaces() {
        assertThatThrownBy(() -> new CharacterName("Jaina P"))
                .isInstanceOf(DomainRuleViolationException.class);
    }

    @Test
    void rejectsSymbols() {
        assertThatThrownBy(() -> new CharacterName("Thr@ll"))
                .isInstanceOf(DomainRuleViolationException.class);
    }

    @Test
    void rejectsEmptyAndBlankNames() {
        assertThatThrownBy(() -> new CharacterName(""))
                .isInstanceOf(DomainRuleViolationException.class);
        assertThatThrownBy(() -> new CharacterName("   "))
                .isInstanceOf(DomainRuleViolationException.class);
    }

    @Test
    void rejectsNull() {
        assertThatThrownBy(() -> new CharacterName(null))
                .isInstanceOf(NullPointerException.class);
    }
}
