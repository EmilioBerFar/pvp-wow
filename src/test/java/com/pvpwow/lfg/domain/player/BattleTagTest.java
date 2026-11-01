package com.pvpwow.lfg.domain.player;

import com.pvpwow.lfg.domain.shared.DomainRuleViolationException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class BattleTagTest {

    @Test
    void trimsAndLowercasesTheNamePart() {
        assertThat(new BattleTag("  Emilio#12345 ").value()).isEqualTo("emilio#12345");
    }

    @Test
    void sameBattleTagInDifferentCaseIsEqual() {
        assertThat(new BattleTag("Emilio#12345")).isEqualTo(new BattleTag("emilio#12345"));
    }

    @Test
    void acceptsShortestNameAndDiscriminator() {
        assertThatNoException().isThrownBy(() -> new BattleTag("abc#1234"));
    }

    @Test
    void acceptsLongestNameAndDiscriminator() {
        assertThatNoException().isThrownBy(() -> new BattleTag("a".repeat(12) + "#12345"));
    }

    @Test
    void rejectsNameShorterThanThree() {
        assertThatThrownBy(() -> new BattleTag("ab#1234"))
                .isInstanceOf(DomainRuleViolationException.class);
    }

    @Test
    void rejectsNameLongerThanTwelve() {
        assertThatThrownBy(() -> new BattleTag("a".repeat(13) + "#1234"))
                .isInstanceOf(DomainRuleViolationException.class);
    }

    @Test
    void rejectsDiscriminatorShorterThanFourOrLongerThanFive() {
        assertThatThrownBy(() -> new BattleTag("emilio#123"))
                .isInstanceOf(DomainRuleViolationException.class);
        assertThatThrownBy(() -> new BattleTag("emilio#123456"))
                .isInstanceOf(DomainRuleViolationException.class);
    }

    @Test
    void rejectsMissingSeparatorOrMissingDiscriminator() {
        assertThatThrownBy(() -> new BattleTag("emilio1234"))
                .isInstanceOf(DomainRuleViolationException.class);
        assertThatThrownBy(() -> new BattleTag("emilio#"))
                .isInstanceOf(DomainRuleViolationException.class);
    }

    @Test
    void rejectsNonAlphanumericNameAndNonDigitDiscriminator() {
        assertThatThrownBy(() -> new BattleTag("emilio.ber#1234"))
                .isInstanceOf(DomainRuleViolationException.class);
        assertThatThrownBy(() -> new BattleTag("emilio#12a4"))
                .isInstanceOf(DomainRuleViolationException.class);
    }

    @Test
    void rejectsNull() {
        assertThatThrownBy(() -> new BattleTag(null))
                .isInstanceOf(NullPointerException.class);
    }
}
