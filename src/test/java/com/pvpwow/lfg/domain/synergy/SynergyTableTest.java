package com.pvpwow.lfg.domain.synergy;

import com.pvpwow.lfg.domain.character.WowClass;
import com.pvpwow.lfg.domain.shared.DomainRuleViolationException;
import org.junit.jupiter.api.Test;

import java.util.EnumMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static com.pvpwow.lfg.domain.character.WowClass.MAGE;
import static com.pvpwow.lfg.domain.character.WowClass.PRIEST;
import static com.pvpwow.lfg.domain.character.WowClass.ROGUE;

class SynergyTableTest {

    @Test
    void lookupIsSymmetricSoArgumentOrderNeverMatters() {
        SynergyTable table = completeTable();

        assertThat(table.score(MAGE, ROGUE)).isEqualTo(table.score(ROGUE, MAGE));
    }

    @Test
    void sameClassPairsExistBecauseMirrorCompsAreReal() {
        SynergyTable table = completeTable();

        assertThat(table.score(MAGE, MAGE)).isNotNull();
    }

    @Test
    void rejectsAMatrixMissingAWholeRow() {
        Map<WowClass, Map<WowClass, SynergyScore>> matrix = completeMatrix();
        matrix.remove(MAGE);

        assertThatThrownBy(() -> SynergyTable.from(matrix))
                .isInstanceOf(DomainRuleViolationException.class)
                .hasMessageContaining("MAGE");
    }

    @Test
    void rejectsAMatrixMissingASinglePair() {
        Map<WowClass, Map<WowClass, SynergyScore>> matrix = completeMatrix();
        matrix.get(MAGE).remove(PRIEST);

        assertThatThrownBy(() -> SynergyTable.from(matrix))
                .isInstanceOf(DomainRuleViolationException.class)
                .hasMessageContaining("MAGE + PRIEST");
    }

    @Test
    void rejectsAnAsymmetricMatrixAndNamesTheCulprit() {
        Map<WowClass, Map<WowClass, SynergyScore>> matrix = completeMatrix();
        matrix.get(MAGE).put(ROGUE, new SynergyScore(99));

        assertThatThrownBy(() -> SynergyTable.from(matrix))
                .isInstanceOf(DomainRuleViolationException.class)
                .hasMessageContaining("not symmetric");
    }

    @Test
    void copiesDeeplySoCallersCannotMutateTheTable() {
        Map<WowClass, Map<WowClass, SynergyScore>> matrix = completeMatrix();
        SynergyTable table = SynergyTable.from(matrix);

        matrix.get(MAGE).put(PRIEST, new SynergyScore(0));

        assertThat(table.score(MAGE, PRIEST)).isNotEqualTo(new SynergyScore(0));
        assertThatThrownBy(() -> table.scores().get(MAGE).put(PRIEST, new SynergyScore(1)))
                .isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> table.scores().put(MAGE, Map.of()))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void rejectsNullData() {
        assertThatThrownBy(() -> SynergyTable.from(null))
                .isInstanceOf(NullPointerException.class);
    }

    private SynergyTable completeTable() {
        return SynergyTable.from(completeMatrix());
    }

    /**
     * A complete symmetric matrix: value depends on the unordered pair, so the
     * diagonal is included and every pair matches in both directions.
     */
    private Map<WowClass, Map<WowClass, SynergyScore>> completeMatrix() {
        Map<WowClass, Map<WowClass, SynergyScore>> matrix = new EnumMap<>(WowClass.class);
        for (WowClass left : WowClass.values()) {
            Map<WowClass, SynergyScore> row = new EnumMap<>(WowClass.class);
            for (WowClass right : WowClass.values()) {
                int low = Math.min(left.ordinal(), right.ordinal());
                int high = Math.max(left.ordinal(), right.ordinal());
                row.put(right, new SynergyScore((low * 13 + high) % 101));
            }
            matrix.put(left, row);
        }
        return matrix;
    }
}
