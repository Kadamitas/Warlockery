package com.kadamitas.warlockery.entity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.kadamitas.warlockery.entity.InfernalHierarchyRules.AuthorityClass;
import com.kadamitas.warlockery.entity.InfernalHierarchyRules.Intent;
import com.kadamitas.warlockery.entity.InfernalHierarchyRules.OrderKind;
import com.kadamitas.warlockery.entity.InfernalHierarchyRules.PhaseState;
import com.kadamitas.warlockery.entity.InfernalHierarchyRules.Rank;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;

final class InfernalHierarchyStateTest {
    private static final UUID ENTITY_ID = new UUID(21L, 84L);
    private static final long NOW = 48_000L;

    @Test
    void emptyStateStartsAtBaselineWithStableStaggeredDecision() {
        final InfernalHierarchyState state = InfernalHierarchyState.empty(Rank.DEMON, ENTITY_ID, NOW);
        assertEquals(InfernalHierarchyState.SCHEMA_VERSION, state.schemaVersion());
        assertEquals(Rank.DEMON, state.rank());
        assertEquals(Intent.IDLE, state.intent());
        assertEquals(AuthorityClass.AUTONOMY, state.authorityClass());
        assertEquals(InfernalHierarchyRules.MORALE_BASELINE, state.morale());
        assertTrue(state.roster().isEmpty());
        assertTrue(state.summons().isEmpty());
        assertFalse(state.phaseCompleted());
        assertEquals(PhaseState.NONE, state.phaseState());
        final long offset = InfernalHierarchyRules.stableOffset(
            ENTITY_ID, InfernalHierarchyRules.DECISION_INTERVAL_TICKS
        );
        assertEquals(NOW + offset, state.cadence().nextDecisionAt());
    }

    @Test
    void roundTripPreservesBoundedSemanticFacts() {
        final UUID leader = new UUID(1L, 2L);
        final UUID member = new UUID(1L, 3L);
        final UUID truce = new UUID(1L, 4L);
        final UUID aggressor = new UUID(1L, 5L);
        final InfernalHierarchyState original = InfernalHierarchyState.empty(Rank.DEMON, ENTITY_ID, NOW)
            .withLeader(Optional.of(leader), Optional.of(Rank.EMBERHORN_ARCHFIEND), NOW + 300L)
            .withOrder(Optional.of(new InfernalHierarchyState.Order(
                OrderKind.HOLD_POST, Optional.of(member), NOW + 150L, 3L, Rank.EMBERHORN_ARCHFIEND)))
            .withTruce(Optional.of(truce), NOW + 100L, NOW, 0L)
            .withAggressor(Optional.of(aggressor), NOW + 400L)
            .withMorale(420, NOW)
            .withAnchor(Optional.of(77L), NOW + 200L);
        final CompoundTag written = original.write();
        final InfernalHierarchyState loaded = InfernalHierarchyState.read(
            written, Rank.DEMON, ENTITY_ID, NOW, false
        );
        assertEquals(leader, loaded.leaderId().orElseThrow());
        assertEquals(Rank.EMBERHORN_ARCHFIEND, loaded.leaderRank().orElseThrow());
        assertEquals(OrderKind.HOLD_POST, loaded.order().orElseThrow().kind());
        assertEquals(member, loaded.order().orElseThrow().targetId().orElseThrow());
        assertEquals(truce, loaded.trucePlayerId().orElseThrow());
        assertEquals(aggressor, loaded.aggressorId().orElseThrow());
        assertEquals(77L, loaded.anchorPos().orElseThrow());
        assertEquals(420, loaded.morale(), "safe morale reloads without free recovery at the same instant");
        assertEquals(Intent.IDLE, loaded.intent(), "no live semantic action resumes from disk");
    }

    @Test
    void unknownSchemaResetsToSafeDefaultsWhilePreservingLegacyPhaseLatch() {
        final CompoundTag alien = new CompoundTag();
        alien.putInt("Version", 99);
        alien.putString("Rank", "abyssal_regent");
        final InfernalHierarchyState loaded = InfernalHierarchyState.read(
            alien, Rank.ABYSSAL_REGENT, ENTITY_ID, NOW, true
        );
        assertEquals(InfernalHierarchyState.SCHEMA_VERSION, loaded.schemaVersion());
        assertTrue(loaded.phaseCompleted(), "the legacy half-health latch survives unknown schemas");
        assertEquals(PhaseState.DONE, loaded.phaseState());
        assertTrue(loaded.roster().isEmpty());
    }

    @Test
    void aPhaseWindowSaveResumesItsExactWindowInsteadOfCancelling() {
        for (final PhaseState window : List.of(PhaseState.TELEGRAPH, PhaseState.COMMIT, PhaseState.RECOVERY)) {
            final CompoundTag written = InfernalHierarchyState.empty(Rank.ABYSSAL_REGENT, ENTITY_ID, NOW)
                .withPhase(window, true, NOW + 25L)
                .write();
            final InfernalHierarchyState loaded = InfernalHierarchyState.read(
                written, Rank.ABYSSAL_REGENT, ENTITY_ID, NOW, false
            );
            assertEquals(window, loaded.phaseState(),
                "a save inside an active phase window resumes that exact window: " + window);
            assertTrue(loaded.phaseCompleted(),
                "the once-per-regent latch survives an active-window resume: " + window);
            assertEquals(NOW + 25L, loaded.phaseDeadline(),
                "a plausible deadline resumes exactly: " + window);
        }
        final InfernalHierarchyState done = InfernalHierarchyState.read(
            InfernalHierarchyState.empty(Rank.ABYSSAL_REGENT, ENTITY_ID, NOW)
                .withPhase(PhaseState.DONE, true, 0L).write(),
            Rank.ABYSSAL_REGENT, ENTITY_ID, NOW, false
        );
        assertEquals(PhaseState.DONE, done.phaseState(), "a completed phase stays completed");
        final CompoundTag decoupled = InfernalHierarchyState.empty(Rank.ABYSSAL_REGENT, ENTITY_ID, NOW)
            .withPhase(PhaseState.TELEGRAPH, true, Long.MAX_VALUE).write();
        decoupled.putBoolean("PhaseCompleted", false);
        final InfernalHierarchyState repaired = InfernalHierarchyState.read(
            decoupled, Rank.ABYSSAL_REGENT, ENTITY_ID, NOW, false
        );
        assertTrue(repaired.phaseCompleted(),
            "a malformed active window without its latch forces the latch so the phase never replays");
        assertTrue(repaired.phaseDeadline() <= NOW + InfernalHierarchyRules.PHASE_TELEGRAPH_TICKS,
            "an implausible phase deadline clamps to its own window horizon");
    }

    @Test
    void rankMismatchIsCorruptionAndResetsOnlyUnsafeState() {
        final CompoundTag written = InfernalHierarchyState.empty(Rank.DEMON, ENTITY_ID, NOW)
            .withMorale(100, NOW).write();
        final InfernalHierarchyState loaded = InfernalHierarchyState.read(
            written, Rank.ABYSSAL_REGENT, ENTITY_ID, NOW, false
        );
        assertEquals(Rank.ABYSSAL_REGENT, loaded.rank(), "the immutable rank guard wins");
        assertEquals(InfernalHierarchyRules.MORALE_BASELINE, loaded.morale());
    }

    @Test
    void malformedUuidsEnumsAndExpiredDeadlinesClearInsteadOfCrashing() {
        final CompoundTag tag = InfernalHierarchyState.empty(Rank.DEMON, ENTITY_ID, NOW).write();
        tag.putString("AggressorId", "not-a-uuid");
        tag.putLong("AggressorExpiresAt", NOW + 100L);
        tag.putString("TrucePlayerId", new UUID(9L, 9L).toString());
        tag.putLong("TruceExpiresAt", NOW - 1L);
        tag.putString("Intent", "no_such_intent");
        final InfernalHierarchyState loaded = InfernalHierarchyState.read(
            tag, Rank.DEMON, ENTITY_ID, NOW, false
        );
        assertTrue(loaded.aggressorId().isEmpty());
        assertTrue(loaded.trucePlayerId().isEmpty(), "expired truces clear on load");
        assertEquals(Intent.IDLE, loaded.intent());
    }

    @Test
    void extremeDeadlinesClampToTheLegalHorizon() {
        final CompoundTag tag = InfernalHierarchyState.empty(Rank.DEMON, ENTITY_ID, NOW)
            .withTruce(Optional.of(new UUID(5L, 5L)), Long.MAX_VALUE, NOW, Long.MAX_VALUE)
            .withAggressor(Optional.of(new UUID(6L, 6L)), Long.MAX_VALUE)
            .write();
        tag.putLong("ActionBackoffUntil", Long.MAX_VALUE);
        final InfernalHierarchyState loaded = InfernalHierarchyState.read(
            tag, Rank.DEMON, ENTITY_ID, NOW, false
        );
        assertTrue(loaded.truceExpiresAt() <= NOW + InfernalHierarchyRules.TRUCE_TICKS);
        assertTrue(loaded.truceBreachUntil() <= NOW + InfernalHierarchyRules.TRUCE_BREACH_TICKS);
        assertTrue(loaded.aggressorExpiresAt() <= NOW + InfernalHierarchyRules.AGGRESSOR_TICKS);
        assertTrue(loaded.actionBackoffUntil() <= NOW + InfernalHierarchyRules.ROUTE_BACKOFF_TICKS);
    }

    @Test
    void rosterAndSummonListsTruncateDeterministicallyToTheirCaps() {
        final java.util.ArrayList<InfernalHierarchyState.Member> oversized = new java.util.ArrayList<>();
        for (int index = 0; index < 12; index++) {
            oversized.add(new InfernalHierarchyState.Member(
                new UUID(3L, index), Rank.DEMON, NOW + 300L
            ));
        }
        final InfernalHierarchyState regent = InfernalHierarchyState.empty(Rank.ABYSSAL_REGENT, ENTITY_ID, NOW)
            .withRoster(oversized, 1L);
        assertEquals(InfernalHierarchyRules.COURT_MEMBER_CAP, regent.roster().size());
        final InfernalHierarchyState archfiend = InfernalHierarchyState.empty(
            Rank.EMBERHORN_ARCHFIEND, ENTITY_ID, NOW).withRoster(oversized, 1L);
        assertEquals(InfernalHierarchyRules.SQUAD_MEMBER_CAP, archfiend.roster().size());
        final InfernalHierarchyState summons = regent.withSummons(
            List.of(new UUID(4L, 1L), new UUID(4L, 2L), new UUID(4L, 3L)), NOW + 100L);
        assertEquals(InfernalHierarchyRules.PHASE_SUMMON_CAP, summons.summons().size());
    }

    @Test
    void expiredLeasesOrdersAndSummonsClearOnLoad() {
        final CompoundTag tag = InfernalHierarchyState.empty(Rank.DEMON, ENTITY_ID, NOW)
            .withLeader(Optional.of(new UUID(7L, 7L)), Optional.of(Rank.ABYSSAL_REGENT), NOW - 1L)
            .withOrder(Optional.of(new InfernalHierarchyState.Order(
                OrderKind.HOLD_COURT, Optional.empty(), NOW - 1L, 1L, Rank.ABYSSAL_REGENT)))
            .write();
        final InfernalHierarchyState loaded = InfernalHierarchyState.read(
            tag, Rank.DEMON, ENTITY_ID, NOW, false
        );
        assertTrue(loaded.leaderId().isEmpty(), "an expired membership lease clears on load");
        assertTrue(loaded.order().isEmpty(), "an expired order clears on load");
        final CompoundTag summonTag = InfernalHierarchyState.empty(Rank.ABYSSAL_REGENT, ENTITY_ID, NOW)
            .withSummons(List.of(new UUID(8L, 8L)), NOW - 1L)
            .write();
        assertTrue(InfernalHierarchyState.read(summonTag, Rank.ABYSSAL_REGENT, ENTITY_ID, NOW, false)
            .summons().isEmpty(), "expired temporary summons clear on load");
    }

    @Test
    void aTemporarySummonKeepsItsOwnExpiryAcrossSaveAndReload() {
        final UUID summoner = new UUID(31L, 7L);
        final long expiry = NOW + 1_000L;
        final InfernalHierarchyState summon = InfernalHierarchyState.empty(Rank.DEMON, ENTITY_ID, NOW)
            .withSummoner(java.util.Optional.of(summoner), expiry);
        assertTrue(summon.summons().isEmpty(), "a temporary body owns no summons of its own");
        assertEquals(expiry, summon.summonExpiresAt(),
            "the temporary body records its own life deadline");
        final CompoundTag written = summon.write();
        assertEquals(0, written.getIntOr("SummonCount", -1),
            "the persisted summon list stays empty for a temporary body");
        assertEquals(expiry, written.getLongOr("SummonExpiresAt", 0L));
        final InfernalHierarchyState reloaded = InfernalHierarchyState.read(
            written, Rank.DEMON, ENTITY_ID, NOW, false
        );
        assertEquals(summoner, reloaded.summonerId().orElseThrow());
        assertEquals(expiry, reloaded.summonExpiresAt(),
            "a reloaded reinforcement keeps a live expiry so it can still self-expire");
        assertTrue(reloaded.summonExpiresAt() > 0L,
            "a zero deadline would leak the reinforcement permanently");
        assertEquals(0L, InfernalHierarchyState.read(written, Rank.DEMON, ENTITY_ID, expiry + 1L, false)
            .summonExpiresAt(), "an already elapsed deadline still clears on load");
        assertEquals(expiry, summon.withSummons(List.of(), 0L).summonExpiresAt(),
            "clearing an empty summon list never discards the body's own deadline");
        assertEquals(0L, InfernalHierarchyState.empty(Rank.ABYSSAL_REGENT, ENTITY_ID, NOW)
            .withSummons(List.of(new UUID(32L, 1L)), NOW + 100L)
            .withSummons(List.of(), 0L)
            .summonExpiresAt(), "a summoner with no summoner id still clears its tracking deadline");
    }

    @Test
    void closedFormMoraleRecoveryAdvancesOnlyTowardBaselineAcrossReload() {
        final CompoundTag tag = InfernalHierarchyState.empty(Rank.DEMON, ENTITY_ID, 1_000L)
            .withMorale(200, 1_000L).write();
        final InfernalHierarchyState later = InfernalHierarchyState.read(
            tag, Rank.DEMON, ENTITY_ID, 1_000L + 800L, false
        );
        assertEquals(220, later.morale(), "offline elapsed ticks recover one point per forty ticks");
        final InfernalHierarchyState distant = InfernalHierarchyState.read(
            tag, Rank.DEMON, ENTITY_ID, Long.MAX_VALUE / 2L, false
        );
        assertEquals(InfernalHierarchyRules.MORALE_BASELINE, distant.morale(),
            "recovery is capped at the six hundred fifty baseline");
    }

    @Test
    void phaseLatchMigrationNeverReplaysThePhase() {
        final InfernalHierarchyState migrated = InfernalHierarchyState.read(
            new CompoundTag(), Rank.ABYSSAL_REGENT, ENTITY_ID, NOW, true
        );
        assertTrue(migrated.phaseCompleted());
        assertEquals(PhaseState.DONE, migrated.phaseState());
        final InfernalHierarchyState untriggered = InfernalHierarchyState.read(
            new CompoundTag(), Rank.ABYSSAL_REGENT, ENTITY_ID, NOW, false
        );
        assertFalse(untriggered.phaseCompleted());
        assertEquals(PhaseState.NONE, untriggered.phaseState());
    }

    @Test
    void intentChangesAdvanceTheGenerationSoStaleActionsCannotResume() {
        final InfernalHierarchyState state = InfernalHierarchyState.empty(Rank.DEMON, ENTITY_ID, NOW);
        assertEquals(state.intentGeneration(), state.withIntent(Intent.IDLE).intentGeneration());
        assertEquals(state.intentGeneration() + 1L, state.withIntent(Intent.RETREAT).intentGeneration());
    }
}
