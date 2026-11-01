package com.pvpwow.lfg.domain.synergy;

import com.pvpwow.lfg.domain.character.WowClass;
import com.pvpwow.lfg.domain.shared.DomainRuleViolationException;

import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;

/**
 * The complete 13×13 synergy matrix between classes.
 *
 * <p>Complete, not sparse: a missing pair would be indistinguishable from "we
 * have no opinion", and the search would silently rank that candidate last.
 * Forcing the full matrix makes the data decision explicit — if nobody has
 * filled a pair, loading the table fails loudly instead of shipping a lie.
 *
 * <p>Symmetric by rule: class A with class B is the same comp as B with A.
 * Same-class pairs are meaningful and included: Mage + Mage (mirror) is a real
 * comp, so the diagonal is not decoration.
 *
 * <p>The table is data, not behaviour. Where it comes from (JSON file, curated
 * list, an adapter) is not the domain's business.
 */
public record SynergyTable(Map<WowClass, Map<WowClass, SynergyScore>> scores) {

    public SynergyTable {
        Objects.requireNonNull(scores, "scores must not be null");

        Map<WowClass, Map<WowClass, SynergyScore>> copy = new EnumMap<>(WowClass.class);
        for (WowClass row : WowClass.values()) {
            Map<WowClass, SynergyScore> source = scores.get(row);
            if (source == null) {
                throw new DomainRuleViolationException(
                        "Synergy table is missing the row for class %s".formatted(row));
            }
            Map<WowClass, SynergyScore> rowCopy = new EnumMap<>(WowClass.class);
            for (WowClass column : WowClass.values()) {
                SynergyScore score = source.get(column);
                if (score == null) {
                    throw new DomainRuleViolationException(
                            "Synergy table is missing the pair %s + %s".formatted(row, column));
                }
                rowCopy.put(column, score);
            }
            copy.put(row, Collections.unmodifiableMap(rowCopy));
        }

        for (WowClass left : WowClass.values()) {
            for (WowClass right : WowClass.values()) {
                SynergyScore forward = copy.get(left).get(right);
                SynergyScore backward = copy.get(right).get(left);
                if (!forward.equals(backward)) {
                    throw new DomainRuleViolationException(
                            "Synergy table is not symmetric: %s + %s = %d but %s + %s = %d"
                                    .formatted(left, right, forward.value(),
                                            right, left, backward.value()));
                }
            }
        }

        scores = Collections.unmodifiableMap(copy);
    }

    /**
     * Builds the table from raw data, validating that it is complete and
     * symmetric. Kept as the expressive entry point even though the canonical
     * constructor validates too: a record's canonical constructor cannot be
     * made more restrictive, so the rule lives in both places by necessity.
     */
    public static SynergyTable from(Map<WowClass, Map<WowClass, SynergyScore>> scores) {
        return new SynergyTable(scores);
    }

    /**
     * Synergy between two classes. Symmetric by construction, so the argument
     * order never matters.
     */
    public SynergyScore score(WowClass left, WowClass right) {
        Objects.requireNonNull(left, "left must not be null");
        Objects.requireNonNull(right, "right must not be null");
        return scores.get(left).get(right);
    }
}
