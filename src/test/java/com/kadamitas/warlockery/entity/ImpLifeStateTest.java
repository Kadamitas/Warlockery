package com.kadamitas.warlockery.entity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.kadamitas.warlockery.entity.ImpLifeRules.Action;
import com.kadamitas.warlockery.entity.ImpLifeRules.Duty;
import com.kadamitas.warlockery.entity.ImpLifeRules.InfernalOrder;
import com.kadamitas.warlockery.entity.ImpLifeRules.Observation;
import com.kadamitas.warlockery.entity.ImpLifeRules.ObservationType;
import com.kadamitas.warlockery.entity.ImpLifeRules.OrderAction;
import com.kadamitas.warlockery.entity.ImpLifeRules.OrderRank;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;

final class ImpLifeStateTest {
    private static final UUID IMP_ID = new UUID(13L, 37L);
    private static final UUID ISSUER = new UUID(66L, 66L);
    private static final long NOW = 48_000L;
    private static final String OVERWORLD = "minecraft:overworld";

    @Test
    void emptyStateHasNoDutyNoActionAndAStableStaggeredDecision() {
        final ImpLifeState state = ImpLifeState.empty(IMP_ID, NOW);
        assertEquals(ImpLifeState.SCHEMA_VERSION, state.schemaVersion());
        assertTrue(state.steadyDuty().isEmpty());
        assertTrue(state.priorDuty().isEmpty());
        assertEquals(Action.NONE, state.action());
        assertTrue(state.anchor().isEmpty());
        assertTrue(state.destination().isEmpty());
        assertTrue(state.threat().isEmpty());
        assertTrue(state.observations().isEmpty());
        assertTrue(state.order().isEmpty());
        assertEquals(0, state.scoutLeg());
        assertFalse(state.reportDelivered());
        assertEquals(0, state.routeFailures());
        assertFalse(state.retreatLatched());
        final long offset = ImpLifeRules.stableOffset(IMP_ID, ImpLifeRules.IDLE_DECISION_TICKS);
        assertEquals(NOW + offset, state.cadence().nextDecisionAt(),
            "decision staggering is deterministic per identity");
    }

    @Test
    void roundTripPreservesSemanticFactsOnly() {
        final BlockPos anchorPos = new BlockPos(64, 70, -128);
        ImpLifeState state = ImpLifeState.empty(IMP_ID, NOW)
            .withDuties(Optional.of(Duty.WATCH), Optional.of(Duty.FOLLOW))
            .withAction(Action.SCOUT_OUT)
            .withAnchor(Optional.of(new ImpLifeState.Anchor(OVERWORLD, anchorPos)))
            .withDestination(Optional.of(new ImpLifeState.Anchor(OVERWORLD, anchorPos.offset(8, 0, 0))))
            .withThreat(Optional.of(new ImpLifeState.Threat(ISSUER, NOW + 150L)))
            .withObservations(List.of(new Observation(
                ObservationType.HOSTILE, anchorPos.offset(4, 0, 4).asLong(),
                Optional.of(ISSUER), NOW, NOW, 900, NOW + 500L)))
            .withOrder(Optional.of(new InfernalOrder(
                ISSUER, OrderRank.ARCHFIEND, new UUID(9L, 9L), 2L, OrderAction.SCOUT,
                Optional.empty(), NOW, NOW + 400L)))
            .withScout(2, true)
            .withActionWindow(NOW, NOW + ImpLifeRules.SCOUT_TOTAL_TICKS)
            .withRouteFailures(2)
            .withRetreatLatched(true);
        final CompoundTag written = state.write();
        final ImpLifeState loaded = ImpLifeState.read(written, IMP_ID, NOW + 10L);

        assertEquals(Optional.of(Duty.WATCH), loaded.steadyDuty());
        assertEquals(Optional.of(Duty.FOLLOW), loaded.priorDuty());
        assertEquals(Action.SCOUT_OUT, loaded.action(), "a scout with a valid anchor may resume");
        assertEquals(Optional.of(new ImpLifeState.Anchor(OVERWORLD, anchorPos)), loaded.anchor());
        assertEquals(Optional.of(new ImpLifeState.Anchor(OVERWORLD, anchorPos.offset(8, 0, 0))),
            loaded.destination(), "a scout destination survives with its action");
        assertEquals(Optional.of(new ImpLifeState.Threat(ISSUER, NOW + 150L)), loaded.threat());
        assertEquals(1, loaded.observations().size());
        assertEquals(Optional.of(ISSUER), loaded.observations().get(0).subjectId());
        assertTrue(loaded.order().isPresent(), "a complete unexpired order row survives for revalidation");
        assertEquals(2, loaded.scoutLeg());
        assertTrue(loaded.reportDelivered(), "report delivery is idempotent across reloads");
        assertEquals(2, loaded.routeFailures());
        assertTrue(loaded.retreatLatched());
    }

    @Test
    void unknownSchemaBecomesSafeEmptyState() {
        final CompoundTag malformed = new CompoundTag();
        malformed.putInt("Version", 9);
        malformed.putString("Action", "ranged_windup");
        final ImpLifeState state = ImpLifeState.read(malformed, IMP_ID, NOW);
        assertEquals(Action.NONE, state.action());
        assertTrue(state.steadyDuty().isEmpty());
        assertTrue(state.observations().isEmpty());
    }

    @Test
    void legacyOwnerFavorAndInfernalKeysAreNeverDuplicatedIntoTheSchema() {
        final CompoundTag written = ImpLifeState.empty(IMP_ID, NOW).write();
        assertFalse(written.contains("WarlockeryCreatureOwner"));
        assertFalse(written.contains("WarlockeryImpFavor"));
        assertFalse(written.contains("WarlockeryInfernalOwner"));
    }

    @Test
    void transientCombatAndWindupActionsNeverResumeFromDisk() {
        for (final Action transientAction : List.of(
            Action.RANGED_WINDUP, Action.CLOSE_ESCAPE, Action.DISENGAGE,
            Action.HAZARD_ESCAPE, Action.INSPECT, Action.PERCH, Action.NPC_ORDER)) {
            final CompoundTag tag = ImpLifeState.empty(IMP_ID, NOW)
                .withAction(transientAction).write();
            assertEquals(Action.NONE, ImpLifeState.read(tag, IMP_ID, NOW).action(),
                transientAction + " must clear on load");
        }
    }

    @Test
    void scoutWithoutAValidAnchorCancelsInsteadOfInventingOne() {
        final CompoundTag tag = ImpLifeState.empty(IMP_ID, NOW)
            .withDuties(Optional.of(Duty.FOLLOW), Optional.empty())
            .withAction(Action.SCOUT_RETURN)
            .withScout(3, false)
            .write();
        final ImpLifeState state = ImpLifeState.read(tag, IMP_ID, NOW);
        assertEquals(Action.NONE, state.action());
        assertTrue(state.destination().isEmpty());
    }

    @Test
    void dutyActionsRequireAMatchingSteadyDuty() {
        final CompoundTag tag = ImpLifeState.empty(IMP_ID, NOW)
            .withAction(Action.FOLLOW)
            .write();
        assertEquals(Action.NONE, ImpLifeState.read(tag, IMP_ID, NOW).action(),
            "a FOLLOW action without a steady duty clears on load");
    }

    @Test
    void malformedRowsAreDroppedAndListsTruncateDeterministically() {
        final CompoundTag tag = ImpLifeState.empty(IMP_ID, NOW).write();
        tag.putString("ThreatId", "not-a-uuid");
        tag.putLong("ThreatExpiresAt", NOW + 100L);
        tag.putString("AnchorDimension", "");
        tag.putLong("AnchorPos", new BlockPos(1, 64, 1).asLong());
        tag.putInt("ObservationCount", 6);
        for (int index = 0; index < 6; index++) {
            final CompoundTag row = new CompoundTag();
            row.putString("Type", index == 5 ? "not-a-type" : "shiny");
            row.putLong("Pos", new BlockPos(index * 5, 64, 0).asLong());
            row.putLong("FirstAt", NOW);
            row.putLong("LastAt", NOW + index);
            row.putInt("Confidence", 5_000);
            row.putLong("ExpiresAt", NOW + 500L);
            tag.put("Observation" + index, row);
        }
        final ImpLifeState state = ImpLifeState.read(tag, IMP_ID, NOW);
        assertTrue(state.threat().isEmpty(), "a malformed threat UUID is dropped");
        assertTrue(state.anchor().isEmpty(), "an anchor without a dimension is dropped");
        assertEquals(ImpLifeRules.MAX_OBSERVATIONS, state.observations().size(),
            "observation rows truncate deterministically to four");
        assertTrue(state.observations().stream().allMatch(row -> row.confidence() <= 1_000),
            "confidence clamps to its bounded range");
    }

    @Test
    void incompleteOrExpiredOrderRowsAreDropped() {
        final CompoundTag missingRank = ImpLifeState.empty(IMP_ID, NOW).write();
        missingRank.putString("OrderIssuer", ISSUER.toString());
        missingRank.putString("OrderAction", "scout");
        missingRank.putLong("OrderExpiresAt", NOW + 300L);
        assertTrue(ImpLifeState.read(missingRank, IMP_ID, NOW).order().isEmpty(),
            "an order row missing its rank is incomplete and dropped");

        final CompoundTag expired = ImpLifeState.empty(IMP_ID, NOW)
            .withOrder(Optional.of(new InfernalOrder(
                ISSUER, OrderRank.REGENT, new UUID(3L, 3L), 1L, OrderAction.WATCH,
                Optional.empty(), NOW - 700L, NOW - 100L)))
            .write();
        assertTrue(ImpLifeState.read(expired, IMP_ID, NOW).order().isEmpty(),
            "an expired order is removed on load");
    }

    @Test
    void hostileFutureDeadlinesClampToTheirHorizons() {
        final CompoundTag tag = ImpLifeState.empty(IMP_ID, NOW)
            .withDuties(Optional.of(Duty.FOLLOW), Optional.empty())
            .withAction(Action.SCOUT_OUT)
            .withAnchor(Optional.of(new ImpLifeState.Anchor(OVERWORLD, new BlockPos(0, 64, 0))))
            .write();
        tag.putLong("ActionTimeoutAt", Long.MAX_VALUE - 4L);
        tag.putLong("RecoveryUntil", Long.MAX_VALUE - 4L);
        tag.putLong("ThreatExpiresAt", Long.MAX_VALUE - 4L);
        tag.putString("ThreatId", ISSUER.toString());
        final ImpLifeState state = ImpLifeState.read(tag, IMP_ID, NOW);
        assertTrue(state.actionTimeoutAt() <= NOW + ImpLifeRules.SCOUT_TOTAL_TICKS,
            "a scout timeout clamps to the six-hundred-tick circuit");
        assertTrue(state.deadlines().recoveryUntil() <= NOW + ImpLifeRules.MAX_FUTURE_HORIZON_TICKS);
        assertTrue(state.threat().orElseThrow().expiresAt() <= NOW + ImpLifeRules.THREAT_EXPIRY_TICKS,
            "threat evidence clamps to its two-hundred-tick expiry");
    }

    @Test
    void windupAndShotProgressNeverResumeFromDisk() {
        final ImpLifeState state = ImpLifeState.empty(IMP_ID, NOW)
            .withDeadlines(new ImpLifeState.Deadlines(0L, 0L, 0L, NOW, NOW));
        final ImpLifeState loaded = ImpLifeState.read(state.write(), IMP_ID, NOW + 5L);
        assertEquals(0L, loaded.deadlines().windupStartedAt(), "windup progress is transient");
        assertEquals(0L, loaded.deadlines().lastShotAt(), "shot cadence restarts safely after load");
    }

    @Test
    void scoutLegAndRouteFailuresClampToTheirBounds() {
        final CompoundTag tag = ImpLifeState.empty(IMP_ID, NOW).write();
        tag.putInt("ScoutLeg", 99);
        tag.putInt("RouteFailures", 99);
        final ImpLifeState state = ImpLifeState.read(tag, IMP_ID, NOW);
        assertEquals(ImpLifeRules.SCOUT_LEGS, state.scoutLeg());
        assertEquals(ImpLifeRules.MAX_ROUTE_FAILURES, state.routeFailures());
    }

    @Test
    void actionEpochSurvivesAndOnlyMovesForward() {
        final ImpLifeState state = ImpLifeState.empty(IMP_ID, NOW).nextEpoch().nextEpoch();
        assertEquals(2L, state.actionEpoch());
        final ImpLifeState loaded = ImpLifeState.read(state.write(), IMP_ID, NOW);
        assertEquals(2L, loaded.actionEpoch());
        assertTrue(loaded.nextEpoch().actionEpoch() > loaded.actionEpoch());
    }

    @Test
    void noRawPathNavigationNodeOrEntityReferenceIsEverSerialized() {
        final CompoundTag written = ImpLifeState.empty(IMP_ID, NOW)
            .withDuties(Optional.of(Duty.FOLLOW), Optional.empty())
            .withAction(Action.FOLLOW)
            .write();
        for (final String key : written.keySet()) {
            assertFalse(key.toLowerCase(java.util.Locale.ROOT).contains("path"),
                "no raw path may serialize: " + key);
            assertFalse(key.toLowerCase(java.util.Locale.ROOT).contains("node"),
                "no navigation node may serialize: " + key);
        }
    }
}
