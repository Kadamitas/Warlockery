package com.kadamitas.warlockery.entity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.kadamitas.warlockery.entity.InfernalHierarchyRules.Rank;
import org.junit.jupiter.api.Test;

final class InfernalHierarchyRuntimeTest {
    @Test
    void observationChannelsUseTheApprovedPerRankBudgets() {
        assertEquals(80, InfernalHierarchyRuntime.observationIntervalTicks(Rank.DEMON));
        assertEquals(80, InfernalHierarchyRuntime.observationIntervalTicks(Rank.EMBERHORN_ARCHFIEND));
        assertEquals(100, InfernalHierarchyRuntime.observationIntervalTicks(Rank.ABYSSAL_REGENT));
        assertEquals(16, InfernalHierarchyRuntime.observationRadius(Rank.DEMON));
        assertEquals(24, InfernalHierarchyRuntime.observationRadius(Rank.EMBERHORN_ARCHFIEND));
        assertEquals(32, InfernalHierarchyRuntime.observationRadius(Rank.ABYSSAL_REGENT));
        assertEquals(12, InfernalHierarchyRuntime.retainedCandidateCap(Rank.DEMON));
        assertEquals(16, InfernalHierarchyRuntime.retainedCandidateCap(Rank.EMBERHORN_ARCHFIEND));
        assertEquals(24, InfernalHierarchyRuntime.retainedCandidateCap(Rank.ABYSSAL_REGENT));
    }

    @Test
    void groupRefreshCadencesMatchTheApprovedDesign() {
        assertEquals(InfernalHierarchyRules.ARCHFIEND_GROUP_REFRESH_TICKS,
            InfernalHierarchyRuntime.groupRefreshIntervalTicks(Rank.EMBERHORN_ARCHFIEND));
        assertEquals(InfernalHierarchyRules.REGENT_GROUP_REFRESH_TICKS,
            InfernalHierarchyRuntime.groupRefreshIntervalTicks(Rank.ABYSSAL_REGENT));
        assertTrue(InfernalHierarchyRuntime.groupRefreshIntervalTicks(Rank.DEMON)
            >= InfernalHierarchyRules.ARCHFIEND_GROUP_REFRESH_TICKS);
    }
}
