package com.kadamitas.warlockery.entity;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class LycanVillagerStateTest {
    private static UUID id(int value) { return new UUID(0, value); }

    @Test void familiarityIsCappedAndIncomingMustBeatDeterministicVictim() {
        var state = LycanVillagerState.fresh(id(99), 0);
        for (int i = 1; i <= 4; i++) state = state.observe(id(i), LycanVillagerRules.RelationshipSource.RESIDENT, i, i * 10L);
        assertEquals(4, state.familiarity().size());
        assertEquals(state, state.observe(id(5), LycanVillagerRules.RelationshipSource.PLAYER, 1, 100));
        state = state.observe(id(5), LycanVillagerRules.RelationshipSource.PLAYER, 2, 100);
        assertFalse(state.familiarity().stream().anyMatch(row -> row.id().equals(id(1))));
        assertTrue(state.familiarity().stream().anyMatch(row -> row.id().equals(id(5))));
    }

    @Test void familiarityGainCapsAndDecayRemovesZeroes() {
        var state = LycanVillagerState.fresh(id(99), 0).observe(id(1), LycanVillagerRules.RelationshipSource.RESIDENT, 20, 0);
        assertEquals(8, state.points(id(1)));
        state = state.decay(72_000);
        assertEquals(7, state.points(id(1)));
        state = LycanVillagerState.fresh(id(99), 0).observe(id(1), LycanVillagerRules.RelationshipSource.RESIDENT, 1, 0).decay(72_000);
        assertEquals(0, state.points(id(1)));
        assertTrue(state.familiarity().isEmpty());
    }

    @Test void thirdRouteFailureCreatesBackoffAndSuccessClearsIt() {
        var state = LycanVillagerState.fresh(id(99), 0).routeFailed(10).routeFailed(20).routeFailed(30);
        assertEquals(3, state.routeFailures());
        assertEquals(130, state.retryAfter());
        state = state.routeSucceeded(140);
        assertEquals(0, state.routeFailures());
        assertEquals(0, state.retryAfter());
        assertEquals(140, state.lastNavigationAt());
    }

    @Test void loadNormalizationBoundsDataAndResetsTransientIntent() {
        var rows = List.of(
            new LycanVillagerState.Familiarity(id(1), 99, -5, LycanVillagerRules.RelationshipSource.RESIDENT),
            new LycanVillagerState.Familiarity(id(2), 4, 8, LycanVillagerRules.RelationshipSource.PLAYER),
            new LycanVillagerState.Familiarity(id(3), 3, 7, LycanVillagerRules.RelationshipSource.RESIDENT),
            new LycanVillagerState.Familiarity(id(4), 2, 6, LycanVillagerRules.RelationshipSource.RESIDENT),
            new LycanVillagerState.Familiarity(id(5), 1, 5, LycanVillagerRules.RelationshipSource.RESIDENT));
        var raw = new LycanVillagerState(999, rows, Optional.of(id(8)), Optional.of(id(9)),
            LycanVillagerRules.Intent.DEFEND, Optional.of(new LycanVillagerState.Anchor("minecraft:overworld", 12L)),
            Long.MAX_VALUE, Long.MAX_VALUE, Long.MAX_VALUE, Long.MAX_VALUE, Long.MAX_VALUE,
            Long.MAX_VALUE, Long.MAX_VALUE, 99, Long.MAX_VALUE, 7);
        var normalized = raw.normalizeAfterLoad(1_000);
        assertEquals(LycanVillagerState.SCHEMA_VERSION, normalized.schemaVersion());
        assertEquals(LycanVillagerRules.Intent.ROUTINE, normalized.intent());
        assertTrue(normalized.recentAggressor().isEmpty());
        assertTrue(normalized.protectedResident().isEmpty());
        assertEquals(4, normalized.familiarity().size());
        assertTrue(normalized.familiarity().stream().allMatch(f -> f.points() >= 0 && f.points() <= 8));
        assertTrue(normalized.warningDeadline() <= 1_020);
        assertTrue(normalized.pursuitExpiry() <= 1_200);
        assertTrue(normalized.withdrawalExpiry() <= 1_100);
        assertEquals(8, normalized.generation());
    }
}
