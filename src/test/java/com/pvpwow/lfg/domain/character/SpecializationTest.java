package com.pvpwow.lfg.domain.character;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Map;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

class SpecializationTest {

    @Test
    void druidHasExactlyItsFourSpecs() {
        assertThat(Specialization.forClass(WowClass.DRUID)).containsExactlyInAnyOrder(
                Specialization.DRUID_BALANCE,
                Specialization.DRUID_FERAL,
                Specialization.DRUID_GUARDIAN,
                Specialization.DRUID_RESTORATION);
    }

    @Test
    void everySpecIsReachableThroughItsOwnClass() {
        for (Specialization spec : Specialization.values()) {
            assertThat(Specialization.forClass(spec.wowClass()))
                    .as("%s should be listed under %s", spec, spec.wowClass())
                    .contains(spec);
        }
    }

    @Test
    void thereAreExactly39Specs() {
        assertThat(Specialization.values()).hasSize(39);
    }

    @Test
    void everyClassHasAtLeastTwoSpecs() {
        for (WowClass clazz : WowClass.values()) {
            assertThat(Specialization.forClass(clazz))
                    .as("%s should have specs", clazz)
                    .hasSizeGreaterThanOrEqualTo(2);
        }
    }

    /**
     * Data-integrity guard: one wrong role in the enum silently breaks comp
     * filtering, so the real game counts are pinned here — 6 tanks,
     * 7 healers, 13 melee, 13 ranged. A typo anywhere fails this test.
     */
    @Test
    void roleCountsMatchTheGame() {
        Map<Role, Long> byRole = Arrays.stream(Specialization.values())
                .collect(Collectors.groupingBy(Specialization::role, Collectors.counting()));

        assertThat(byRole.getOrDefault(Role.TANK, 0L)).isEqualTo(6);
        assertThat(byRole.getOrDefault(Role.HEALER, 0L)).isEqualTo(7);
        assertThat(byRole.getOrDefault(Role.MELEE_DPS, 0L)).isEqualTo(13);
        assertThat(byRole.getOrDefault(Role.RANGED_DPS, 0L)).isEqualTo(13);
    }
}
