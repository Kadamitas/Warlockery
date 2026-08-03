package com.kadamitas.warlockery.world;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.kadamitas.warlockery.world.VillageAssaultData.AssaultState;
import com.kadamitas.warlockery.world.VillageAssaultRules.AssaultKind;
import com.kadamitas.warlockery.world.VillageAssaultRules.SettlementKind;
import java.util.List;
import java.util.Set;
import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;

final class VillageAssaultDataTest {
    @Test
    void oneSavedDataInstanceCannotStartDuplicateAssaults() {
        final VillageAssaultData data = new VillageAssaultData();
        assertTrue(data.begin(BlockPos.ZERO, AssaultKind.VAMPIRE, SettlementKind.HUMAN, 100L));
        assertFalse(data.begin(BlockPos.ZERO, AssaultKind.WEREWOLF, SettlementKind.HUMAN, 101L));
        final AssaultState active = data.active().orElseThrow();
        assertEquals(AssaultKind.VAMPIRE, active.kind());
        assertEquals(0, active.wave());
    }

    @Test
    void finishingAnAssaultClearsStateAndSchedulesARealCooldown() {
        final VillageAssaultData data = new VillageAssaultData();
        assertTrue(data.begin(BlockPos.ZERO, AssaultKind.GOBLIN, SettlementKind.HUMAN, 500L));
        data.finish(1_000L, 0L, 1.0D);
        assertTrue(data.active().isEmpty());
        assertTrue(data.nextAttempt() >= 1_000L + VillageAssaultRules.MINIMUM_DELAY_TICKS);
        assertTrue(data.nextAttempt() <= 1_000L + VillageAssaultRules.MAXIMUM_DELAY_TICKS);
    }

    @Test
    void waveTransitionsPreserveIdentityAndPreventStateSpam() {
        final AssaultState initial = new AssaultState(
            new BlockPos(3, 70, 4),
            AssaultKind.WEREWOLF,
            SettlementKind.HOBGOBLIN,
            0,
            100L,
            10_000L,
            false,
            List.of("first", "first"),
            0,
            VillageAssaultRules.objectiveQuota(AssaultKind.WEREWOLF),
            List.of(),
            false
        );
        assertEquals(List.of("first"), initial.participants());
        final AssaultState spawned = initial.waveSpawned(1);
        assertTrue(spawned.awaitingClear());
        assertEquals(1, spawned.wave());
        final AssaultState cleared = spawned.waveCleared(1_000L);
        assertFalse(cleared.awaitingClear());
        assertEquals(1_000L + VillageAssaultRules.INTERMISSION_TICKS, cleared.nextWaveTime());
        assertEquals(initial.center(), cleared.center());
        assertEquals(initial.kind(), cleared.kind());
        assertEquals(initial.settlement(), cleared.settlement());
    }

    @Test
    void participantTrackingIsUniqueAndIdempotent() {
        final AssaultState initial = new AssaultState(
            BlockPos.ZERO, AssaultKind.VAMPIRE, SettlementKind.HUMAN, 1, 100L, 1_000L, true, List.of("a"),
            0, VillageAssaultRules.objectiveQuota(AssaultKind.VAMPIRE), List.of(), false
        );
        assertSame(initial, initial.addParticipants(Set.of("a")));
        final AssaultState expanded = initial.addParticipants(Set.of("b", "c"));
        assertEquals(Set.of("a", "b", "c"), Set.copyOf(expanded.participants()));
        assertEquals(3, expanded.participants().size());
    }

    @Test
    void invalidPersistedWaveStateIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> new AssaultState(
            BlockPos.ZERO, AssaultKind.GOBLIN, SettlementKind.HUMAN, 4, 0L, 0L, false, List.of(),
            0, 0, List.of(), false
        ));
        assertThrows(IllegalArgumentException.class, () -> new AssaultState(
            BlockPos.ZERO, AssaultKind.GOBLIN, SettlementKind.HUMAN, 0, -1L, 0L, false, List.of(),
            0, 0, List.of(), false
        ));
    }

    @Test
    void supernaturalObjectivesCountOnlyUniqueVictimsAndTriggerRetreatAtQuota() {
        for (final AssaultKind kind : Set.of(AssaultKind.VAMPIRE, AssaultKind.WEREWOLF)) {
            final VillageAssaultData data = new VillageAssaultData();
            assertTrue(data.begin(BlockPos.ZERO, kind, SettlementKind.HUMAN, 0L));
            AssaultState state = data.active().orElseThrow();
            final AssaultState beforeObjective = state;
            assertThrows(IllegalStateException.class, () -> beforeObjective.beginRaiderRetreat(100L));
            for (int index = 0; index < state.objectiveQuota(); index++) {
                final String victim = "victim-" + index;
                state = state.recordObjectiveVictim(victim);
                final AssaultState duplicate = state.recordObjectiveVictim(victim);
                assertSame(state, duplicate);
            }
            assertEquals(state.objectiveQuota(), state.objectiveProgress());
            assertEquals(state.objectiveQuota(), state.objectiveVictims().size());
            assertTrue(state.objectiveSatisfied());
            final AssaultState retreat = state.beginRaiderRetreat(1_000L);
            assertTrue(retreat.raidersRetreating());
            assertTrue(retreat.awaitingClear());
            assertEquals(1_000L + VillageAssaultRules.ESCAPE_LIFETIME_TICKS, retreat.nextWaveTime());
            assertSame(retreat, retreat.recordObjectiveVictim("late-victim"));
        }
    }

    @Test
    void goblinAssaultsHaveNoCivilianObjectiveOrRetreatShortcut() {
        final VillageAssaultData data = new VillageAssaultData();
        assertTrue(data.begin(BlockPos.ZERO, AssaultKind.GOBLIN, SettlementKind.HUMAN, 0L));
        final AssaultState state = data.active().orElseThrow();
        assertEquals(0, state.objectiveQuota());
        assertSame(state, state.recordObjectiveVictim("villager"));
        assertFalse(state.objectiveSatisfied());
        assertThrows(IllegalStateException.class, () -> state.beginRaiderRetreat(100L));
    }

    @Test
    void configurableFrequencyRemainsValidatedAndDeterministic() {
        final long base = VillageAssaultRules.nextDelay(123L);
        assertEquals(Math.round(base * 0.5D), VillageAssaultRules.nextDelay(123L, 0.5D));
        assertEquals(Math.round(base * 2.0D), VillageAssaultRules.nextDelay(123L, 2.0D));
        assertThrows(IllegalArgumentException.class, () -> VillageAssaultRules.nextDelay(0L, 0.0D));
        assertThrows(IllegalArgumentException.class, () -> VillageAssaultRules.nextDelay(0L, Double.NaN));
    }
}
