package com.pvpwow.lfg.domain.character;

import com.pvpwow.lfg.domain.shared.DomainRuleViolationException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RealmTest {

    @Test
    void trimsSurroundingWhitespace() {
        assertThat(new Realm("  Sanguino ").value()).isEqualTo("sanguino");
    }

    @Test
    void collapsesInnerWhitespaceRunsToOneSpace() {
        assertThat(new Realm("Los   Errantes").value()).isEqualTo("los errantes");
    }

    @Test
    void keepsSingleInnerSpaces() {
        assertThat(new Realm("Los Errantes").value()).isEqualTo("los errantes");
    }

    @Test
    void sameRealmWrittenDifferentlyIsEqual() {
        assertThat(new Realm("SANGUINO")).isEqualTo(new Realm("sanguino"));
    }

    @Test
    void rejectsBlankRealms() {
        assertThatThrownBy(() -> new Realm(""))
                .isInstanceOf(DomainRuleViolationException.class);
        assertThatThrownBy(() -> new Realm("    "))
                .isInstanceOf(DomainRuleViolationException.class);
    }

    @Test
    void rejectsNull() {
        assertThatThrownBy(() -> new Realm(null))
                .isInstanceOf(NullPointerException.class);
    }
}
