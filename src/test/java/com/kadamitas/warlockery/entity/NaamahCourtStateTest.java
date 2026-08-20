package com.kadamitas.warlockery.entity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.kadamitas.warlockery.entity.NaamahCourtRules.Action;
import com.kadamitas.warlockery.entity.NaamahCourtRules.Phase;
import com.kadamitas.warlockery.entity.HazardEscapeRules.Hazard;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;

final class NaamahCourtStateTest {
    @Test
    void detectedLocalHazardPersistsUntilTheNextChargedScan() {
        final NaamahCourtState detected = NaamahCourtState.empty()
            .withLocalHazard(Optional.of(Hazard.CONTACT));

        final NaamahCourtState restored = NaamahCourtState.read(
            detected.write(), 100L, 100.0F, 100.0F
        );

        assertEquals(Optional.of(Hazard.CONTACT), restored.localHazard());
        assertTrue(restored.withLocalHazard(Optional.empty()).localHazard().isEmpty());
    }

    @Test
    void telegraphedActionBindsAndPersistsOneImmutableTargetIdentity() {
        final UUID original = UUID.randomUUID();
        final UUID replacement = UUID.randomUUID();
        final NaamahCourtState bound = NaamahCourtState.empty()
            .withChallenger(original, 500L)
            .withDestination(new BlockPos(4, 64, 4), 500L)
            .beginAction(Action.VEIL_STEP, 100L, original, "minecraft:overworld")
            .withChallenger(replacement, 500L);

        assertEquals(Optional.of(original), bound.actionTarget());
        assertEquals(Optional.of("minecraft:overworld"), bound.actionDimension());

        final NaamahCourtState restored = NaamahCourtState.read(
            bound.write(), 110L, 100.0F, 100.0F
        );
        assertEquals(Optional.of(original), restored.actionTarget());
        assertEquals(Optional.of("minecraft:overworld"), restored.actionDimension());
        assertTrue(restored.finishAction().actionTarget().isEmpty());
        assertTrue(restored.finishAction().actionDimension().isEmpty());

        final CompoundTag missingTarget = bound.write();
        missingTarget.remove("ActionTarget");
        final NaamahCourtState safelyCancelled = NaamahCourtState.read(
            missingTarget, 110L, 100.0F, 100.0F
        );
        assertEquals(Action.NONE, safelyCancelled.action());
        assertTrue(safelyCancelled.actionTarget().isEmpty());
        assertTrue(safelyCancelled.actionDimension().isEmpty());
    }

    @Test
    void semanticCourtStateRoundTripsWithoutPathsOrEntityReferences() {
        final UUID challenger = UUID.randomUUID();
        final UUID attacker = UUID.randomUUID();
        final NaamahCourtState state = NaamahCourtState.empty()
            .withAnchor("minecraft:overworld", new BlockPos(4, 70, -8))
            .latchPhase(30.0F, 100.0F)
            .withChallenger(challenger, 600L)
            .rememberAttacker(attacker, 500L)
            .withDestination(new BlockPos(8, 70, -8), 180L)
            .beginAction(Action.VEIL_STEP, 100L)
            .withSchedule(150L, 300L, 340L, 400L, 120L)
            .withRouteRetry(2, 450L);

        assertEquals(state, NaamahCourtState.read(state.write(), 110L, 30.0F, 100.0F));
    }

    @Test
    void phaseAndHighestPhaseRemainMonotonicAcrossHealingAndMigration() {
        final CompoundTag tag = NaamahCourtState.empty()
            .latchPhase(20.0F, 100.0F)
            .write();
        tag.putString("Phase", Phase.ENTHRONED.name());

        final NaamahCourtState migrated = NaamahCourtState.read(tag, 0L, 100.0F, 100.0F);
        assertEquals(Phase.SOVEREIGN_REFUSAL, migrated.phase());
        assertEquals(Phase.SOVEREIGN_REFUSAL, migrated.highestPhase());
        assertEquals(Phase.SOVEREIGN_REFUSAL, migrated.latchPhase(100.0F, 100.0F).phase());
    }

    @Test
    void actionAndIdentityExpiriesReleaseStaleStateAfterLoad() {
        final NaamahCourtState stale = NaamahCourtState.empty()
            .withChallenger(UUID.randomUUID(), 90L)
            .rememberAttacker(UUID.randomUUID(), 80L)
            .withDestination(new BlockPos(3, 64, 3), 95L)
            .beginAction(Action.COURT_WAVE, 20L)
            .withSchedule(10L, 20L, 30L, 40L, 5L);

        final NaamahCourtState recovered = NaamahCourtState.read(stale.write(), 101L, 50.0F, 100.0F);
        assertEquals(Action.NONE, recovered.action());
        assertTrue(recovered.challenger().isEmpty());
        assertTrue(recovered.recentAttacker().isEmpty());
        assertTrue(recovered.destination().isEmpty());
        assertEquals(101L, recovered.nextDecisionAt());
        assertEquals(101L, recovered.nextCandidateScanAt());
        assertEquals(101L, recovered.nextShadeScanAt());
        assertEquals(101L, recovered.lastNavigationAt());
    }

    @Test
    void conclusionPersistsOneOwnerAndClearsCombatState() {
        final UUID owner = UUID.randomUUID();
        final NaamahCourtState concluded = NaamahCourtState.empty()
            .withChallenger(owner, 500L)
            .rememberAttacker(owner, 500L)
            .withDestination(new BlockPos(4, 64, 4), 500L)
            .beginAction(Action.DREAM_APPROACH, 100L)
            .conclude(owner);

        assertTrue(concluded.audienceConcluded());
        assertEquals(Optional.of(owner), concluded.concludedOwner());
        assertEquals(Phase.AUDIENCE_CONCLUDED, concluded.phase());
        assertEquals(Action.NONE, concluded.action());
        assertTrue(concluded.challenger().isEmpty());
        assertTrue(concluded.recentAttacker().isEmpty());
        assertTrue(concluded.destination().isEmpty());
        assertEquals(concluded, NaamahCourtState.read(concluded.write(), 0L, 100.0F, 100.0F));
    }

    @Test
    void unknownSchemaAndInvalidEnumsRetainOnlyValidAnchorAndSafeImpliedPhase() {
        final CompoundTag tag = NaamahCourtState.empty()
            .withAnchor("minecraft:overworld", new BlockPos(7, 80, 9))
            .withChallenger(UUID.randomUUID(), 1_000L)
            .beginAction(Action.COURT_WAVE, 100L)
            .write();
        tag.putInt("SchemaVersion", 99);
        tag.putString("Phase", "not_a_phase");
        tag.putString("Action", "not_an_action");
        tag.putString("Challenger", "not-a-uuid");

        final NaamahCourtState recovered = NaamahCourtState.read(tag, 200L, 60.0F, 100.0F);
        assertEquals(Optional.of("minecraft:overworld"), recovered.anchorDimension());
        assertEquals(Optional.of(new BlockPos(7, 80, 9)), recovered.anchor());
        assertEquals(Phase.CHORUS_OF_WAVES, recovered.phase());
        assertEquals(Action.NONE, recovered.action());
        assertTrue(recovered.challenger().isEmpty());
    }

    @Test
    void missingFieldsInvalidUuidsAndExcessRetriesUseBoundedDefaults() {
        final NaamahCourtState empty = NaamahCourtState.read(new CompoundTag(), 0L, 100.0F, 100.0F);
        assertEquals(Phase.ENTHRONED, empty.phase());
        assertFalse(empty.anchor().isPresent());

        final CompoundTag tag = NaamahCourtState.empty().write();
        tag.putInt("RouteFailures", 500);
        tag.putString("RecentAttacker", "bad");
        tag.putString("ConcludedOwner", "also-bad");
        final NaamahCourtState bounded = NaamahCourtState.read(tag, 0L, 100.0F, 100.0F);
        assertEquals(NaamahCourtRules.MAX_ROUTE_FAILURES, bounded.routeFailures());
        assertTrue(bounded.recentAttacker().isEmpty());
        assertTrue(bounded.concludedOwner().isEmpty());
    }

    @Test
    void thirdRejectedRouteClearsDestinationAndStartsCooldownWhileSuccessResetsFailures() {
        final BlockPos destination = new BlockPos(7, 64, 7);
        NaamahCourtState state = NaamahCourtState.empty().withDestination(destination, 1_000L);

        state = state.recordRouteResult(false, 100L);
        assertEquals(1, state.routeFailures());
        assertEquals(Optional.of(destination), state.destination());
        state = state.recordRouteResult(false, 120L);
        assertEquals(2, state.routeFailures());
        assertEquals(Optional.of(destination), state.destination());
        state = state.recordRouteResult(false, 140L);
        assertEquals(NaamahCourtRules.MAX_ROUTE_FAILURES, state.routeFailures());
        assertTrue(state.destination().isEmpty());
        assertTrue(state.retryAfter() >= 140L + NaamahCourtRules.ROUTE_RETRY_TICKS);

        state = state.recordRouteResult(true, 260L);
        assertEquals(0, state.routeFailures());
        assertEquals(0L, state.retryAfter());
    }

    @Test
    void sameSchemaConclusionPhaseFlagAndOwnerMustAgree() {
        final CompoundTag phaseOnly = NaamahCourtState.empty().write();
        phaseOnly.putString("Phase", Phase.AUDIENCE_CONCLUDED.name());
        phaseOnly.putString("HighestPhase", Phase.AUDIENCE_CONCLUDED.name());
        final NaamahCourtState recoveredPhase = NaamahCourtState.read(phaseOnly, 500L, 100.0F, 100.0F);
        assertFalse(recoveredPhase.audienceConcluded());
        assertTrue(recoveredPhase.concludedOwner().isEmpty());
        assertEquals(Phase.ENTHRONED, recoveredPhase.phase());
        assertEquals(Phase.ENTHRONED, recoveredPhase.highestPhase());

        final CompoundTag mismatchedPhase = NaamahCourtState.empty().write();
        mismatchedPhase.putBoolean("AudienceConcluded", true);
        mismatchedPhase.putString("ConcludedOwner", UUID.randomUUID().toString());
        mismatchedPhase.putString("Phase", Phase.CHORUS_OF_WAVES.name());
        final NaamahCourtState recoveredFlag = NaamahCourtState.read(mismatchedPhase, 500L, 100.0F, 100.0F);
        assertFalse(recoveredFlag.audienceConcluded());
        assertTrue(recoveredFlag.concludedOwner().isEmpty());
        assertEquals(Phase.ENTHRONED, recoveredFlag.phase());

        final CompoundTag ownerless = NaamahCourtState.empty().write();
        ownerless.putBoolean("AudienceConcluded", true);
        ownerless.putString("Phase", Phase.AUDIENCE_CONCLUDED.name());
        final NaamahCourtState recoveredOwner = NaamahCourtState.read(ownerless, 500L, 100.0F, 100.0F);
        assertFalse(recoveredOwner.audienceConcluded());
        assertTrue(recoveredOwner.concludedOwner().isEmpty());
    }

    @Test
    void malformedWindowsAndExtremeDeadlinesResetWithoutFreezingCourtWork() {
        final CompoundTag tag = NaamahCourtState.empty().write();
        tag.putString("Action", Action.DREAM_APPROACH.name());
        tag.putLong("ActionStartedAt", 300L);
        tag.putLong("ActionExecuteAt", 250L);
        tag.putLong("RecoverUntil", 700L);
        tag.putString("Challenger", UUID.randomUUID().toString());
        tag.putLong("ChallengerExpiresAt", Long.MAX_VALUE);
        tag.putString("RecentAttacker", UUID.randomUUID().toString());
        tag.putLong("AttackerExpiresAt", Long.MAX_VALUE);
        tag.putLong("Destination", new BlockPos(4, 64, 4).asLong());
        tag.putLong("DestinationExpiresAt", Long.MAX_VALUE);
        tag.putLong("NextDecisionAt", Long.MAX_VALUE);
        tag.putLong("NextCandidateScanAt", Long.MAX_VALUE);
        tag.putLong("NextShadeScanAt", Long.MAX_VALUE);
        tag.putLong("NextAmbientFeedbackAt", Long.MAX_VALUE);
        tag.putLong("LastNavigationAt", Long.MAX_VALUE);
        tag.putInt("RouteFailures", NaamahCourtRules.MAX_ROUTE_FAILURES);
        tag.putLong("RetryAfter", Long.MAX_VALUE);

        final NaamahCourtState recovered = NaamahCourtState.read(tag, 200L, 100.0F, 100.0F);
        assertEquals(Action.NONE, recovered.action());
        assertEquals(0L, recovered.actionStartedAt());
        assertEquals(0L, recovered.actionExecuteAt());
        assertTrue(recovered.recoverUntil() <= 200L + NaamahCourtRules.MIN_RECOVERY_TICKS);
        assertTrue(recovered.challenger().isEmpty());
        assertTrue(recovered.recentAttacker().isEmpty());
        assertTrue(recovered.destination().isEmpty());
        assertEquals(200L, recovered.nextDecisionAt());
        assertEquals(200L, recovered.nextCandidateScanAt());
        assertEquals(200L, recovered.nextShadeScanAt());
        assertEquals(200L, recovered.nextAmbientFeedbackAt());
        assertEquals(200L, recovered.lastNavigationAt());
        assertEquals(0, recovered.routeFailures());
        assertEquals(0L, recovered.retryAfter());
    }
}
