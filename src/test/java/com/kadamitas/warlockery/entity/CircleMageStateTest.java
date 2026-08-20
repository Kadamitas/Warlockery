package com.kadamitas.warlockery.entity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.kadamitas.warlockery.entity.CircleMageRules.Action;
import com.kadamitas.warlockery.entity.CircleMageRules.Mode;
import com.kadamitas.warlockery.entity.CircleMageRules.TargetSource;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;

/** Defaults, round trip, owner non-duplication, coupling, focus persistence, and cancellation. */
final class CircleMageStateTest {
    private static final String HERE = "minecraft:overworld";
    private static final String ELSEWHERE = "minecraft:the_end";

    @Test
    void defaultsAreSafeAndCompletelyIdle() {
        final CircleMageState state = CircleMageState.empty();
        assertEquals(CircleMageState.SCHEMA_VERSION, state.schemaVersion());
        assertEquals(Mode.IDLE, state.mode());
        assertFalse(state.anchor().present());
        assertFalse(state.threat().present());
        assertFalse(state.action().pending());
        assertFalse(state.study().focusPrepared());
        assertFalse(state.session().present());
    }

    @Test
    void theExistingOwnerUuidIsNeverDuplicatedIntoThisState() {
        final List<String> componentNames = java.util.Arrays.stream(
                CircleMageState.class.getRecordComponents())
            .map(java.lang.reflect.RecordComponent::getName)
            .toList();
        assertTrue(componentNames.stream().noneMatch(name -> name.toLowerCase().contains("owner")),
            "CreatureBehaviorState remains the single authoritative owner store");
        final CompoundTag tag = CircleMageState.empty()
            .withSession(CircleMageState.Session.joined(UUID.randomUUID(), HERE, 42L, 1))
            .write();
        assertFalse(tag.contains("Owner"));
        assertFalse(tag.contains("Roster"), "roster membership lives in the SavedData, never per entity");
    }

    @Test
    void aCompleteRoundTripPreservesEveryIndependentlyValidFact() {
        final CircleMageState state = CircleMageState.empty()
            .withAnchor(new CircleMageState.Anchor(Optional.of(new BlockPos(3, 70, 3)), Optional.of(HERE)))
            .withStudy(new CircleMageState.Study(true, Optional.of(new BlockPos(1, 64, 1)),
                Optional.of(HERE), 800, 60))
            .withCadence(new CircleMageState.Cadence(30, 0, 0, 0, 0, 0, 0, 2, 40));

        final CircleMageState loaded = CircleMageState.read(state.write(), HERE);
        assertEquals(new BlockPos(3, 70, 3), loaded.anchor().position().orElseThrow());
        assertTrue(loaded.study().focusPrepared(), "focus is an independently valid persisted fact");
        assertEquals(800, loaded.study().studyCooldownTicks());
        assertEquals(60, loaded.study().searchCooldownTicks());
        assertEquals(30, loaded.cadence().castRecoveryTicks());
        assertFalse(loaded.study().hasWorkstation(), "a destination is transient");
        assertEquals(0, loaded.cadence().routeFailures());
    }

    @Test
    void encodedStateStaysWellUnderTheDeclaredCeiling() {
        final CompoundTag tag = CircleMageState.empty()
            .withAnchor(new CircleMageState.Anchor(Optional.of(new BlockPos(8, 64, 8)), Optional.of(HERE)))
            .withThreat(CircleMageState.Threat.of(UUID.randomUUID(), HERE, TargetSource.PEER_REPORT))
            .withAction(CircleMageState.ActionState.bolt(UUID.randomUUID(), HERE, true))
            .withStudy(new CircleMageState.Study(true, Optional.of(BlockPos.ZERO), Optional.of(HERE), 1_200, 120))
            .withSession(CircleMageState.Session.joined(UUID.randomUUID(), HERE, 123_456L, 2))
            .withCadence(new CircleMageState.Cadence(50, 100, 20, 10, 40, 40, 100, 3, 100))
            .write();
        assertTrue(encodedBytes(tag) < CircleMageRules.MAX_STATE_BYTES);
    }

    @Test
    void unknownOrMalformedSchemaFallsBackToSafeDefaults() {
        assertEquals(CircleMageState.empty(), CircleMageState.read(null, HERE));
        final CompoundTag future = CircleMageState.empty().write();
        future.putInt("Version", 99);
        assertEquals(CircleMageState.empty(), CircleMageState.read(future, HERE));
        assertEquals(CircleMageState.empty(), CircleMageState.read(new CompoundTag(), HERE));
    }

    @Test
    void loadingCancelsEveryActionTargetReportAndSession() {
        final CompoundTag tag = CircleMageState.empty()
            .withThreat(CircleMageState.Threat.of(UUID.randomUUID(), HERE, TargetSource.DIRECT))
            .withAction(CircleMageState.ActionState.bolt(UUID.randomUUID(), HERE, true))
            .withSession(CircleMageState.Session.joined(UUID.randomUUID(), HERE, 7L, 1))
            .withMode(Mode.DEFENDING)
            .withStudy(new CircleMageState.Study(true, Optional.empty(), Optional.empty(), 500, 0))
            .write();
        final CircleMageState loaded = CircleMageState.read(tag, HERE);
        assertEquals(Mode.IDLE, loaded.mode());
        assertFalse(loaded.threat().present());
        assertFalse(loaded.action().pending(), "a bolt is never replayed after a reload");
        assertFalse(loaded.session().present(), "no missed conclave is ever replayed");
        assertTrue(loaded.study().focusPrepared());
        assertEquals(500, loaded.study().studyCooldownTicks());
    }

    @Test
    void aDimensionMismatchDropsTheAnchorWithoutErasingFocus() {
        final CompoundTag tag = CircleMageState.empty()
            .withAnchor(new CircleMageState.Anchor(Optional.of(BlockPos.ZERO), Optional.of(ELSEWHERE)))
            .withStudy(new CircleMageState.Study(true, Optional.empty(), Optional.empty(), 0, 0))
            .write();
        final CircleMageState loaded = CircleMageState.read(tag, HERE);
        assertFalse(loaded.anchor().present());
        assertTrue(loaded.study().focusPrepared());
    }

    @Test
    void coupledActionReportAndSessionIdentitiesAreImmutableOrCollapse() {
        assertEquals(Action.NONE, new CircleMageState.ActionState(
            Action.BOLT, Optional.of(UUID.randomUUID()), Optional.empty(), true, 12).action());
        assertEquals(Action.NONE, new CircleMageState.ActionState(
            Action.BOLT, Optional.empty(), Optional.of(HERE), true, 12).action());
        assertEquals(Action.NONE, new CircleMageState.ActionState(
            Action.STUDY, Optional.empty(), Optional.of(HERE), true, 60).action(),
            "a study rehearsal never reserves a bolt focus");

        assertFalse(new CircleMageState.Threat(
            Optional.of(UUID.randomUUID()), Optional.empty(), TargetSource.DIRECT, 80).present());
        // An expired report is NOT collapsed here any more: tick dispatch ends the phase, which
        // is what makes the transition countable. Covered by
        // anExpiredThreatStaysObservableSoTickDispatchCanEndThePhaseExactlyOnce.

        assertFalse(new CircleMageState.Session(
            Optional.of(UUID.randomUUID()), Optional.empty(), 5L, 1, 100).present());
        assertEquals(CircleMageRules.MAX_SESSION_SIZE - 1, new CircleMageState.Session(
            Optional.of(UUID.randomUUID()), Optional.of(HERE), 5L, 99, 100).slot(),
            "a corrupt slot clamps into 0..2");
    }

    @Test
    void everyStoredDurationIsClampedIntoItsDeclaredBound() {
        final CircleMageState.Cadence cadence = new CircleMageState.Cadence(
            Integer.MAX_VALUE, Integer.MAX_VALUE, Integer.MAX_VALUE, Integer.MAX_VALUE,
            Integer.MAX_VALUE, Integer.MAX_VALUE, Integer.MAX_VALUE, Integer.MAX_VALUE, Integer.MIN_VALUE
        );
        assertEquals(CircleMageRules.BOLT_RECOVERY_TICKS, cadence.castRecoveryTicks());
        assertEquals(CircleMageRules.WITHDRAW_TICKS, cadence.withdrawalTicks());
        assertEquals(CircleMageRules.AURA_INTERVAL_TICKS, cadence.auraTicks());
        assertEquals(CircleMageRules.OWNER_CHECK_INTERVAL_TICKS, cadence.ownerCheckTicks());
        assertEquals(CircleMageRules.PEER_SCAN_INTERVAL_TICKS, cadence.peerScanTicks());
        assertEquals(CircleMageRules.SAFE_STEP_INTERVAL_TICKS, cadence.safeStepTicks());
        assertEquals(CircleMageRules.MAX_ROUTE_FAILURES, cadence.routeFailures());
        assertEquals(0, cadence.routeRetryTicks());

        final CircleMageState.Study study = new CircleMageState.Study(
            true, Optional.of(BlockPos.ZERO), Optional.of(HERE), Integer.MAX_VALUE, Integer.MAX_VALUE
        );
        assertEquals(CircleMageRules.STUDY_COOLDOWN_TICKS, study.studyCooldownTicks());
        assertEquals(CircleMageRules.STUDY_SEARCH_INTERVAL_TICKS, study.searchCooldownTicks());
    }

    @Test
    void theModeIsStoredExactlyAsSetBecauseTheRuntimeOwnsIt() {
        // The record no longer second-guesses the mode. Tick dispatch sets it on every branch, so
        // a silent constructor rewrite would only hide a runtime that forgot to.
        for (final Mode mode : Mode.values()) {
            assertEquals(mode, CircleMageState.empty().withMode(mode).mode());
        }
        assertEquals(Mode.IDLE, CircleMageState.read(
            CircleMageState.empty().withMode(Mode.DEFENDING).write(), HERE).mode(),
            "a reloaded Mage still always resumes idle");
    }

    @Test
    void cancellingLiveWorkClearsActionTargetSessionAndDestinationOnly() {
        final CircleMageState busy = CircleMageState.empty()
            .withThreat(CircleMageState.Threat.of(UUID.randomUUID(), HERE, TargetSource.OWNER))
            .withAction(CircleMageState.ActionState.bolt(UUID.randomUUID(), HERE, true))
            .withSession(CircleMageState.Session.joined(UUID.randomUUID(), HERE, 3L, 0))
            .withStudy(new CircleMageState.Study(true, Optional.of(BlockPos.ZERO), Optional.of(HERE), 700, 30));
        final CircleMageState canceled = busy.cancelLiveWork();
        assertFalse(canceled.action().pending());
        assertFalse(canceled.threat().present());
        assertFalse(canceled.session().present());
        assertFalse(canceled.study().hasWorkstation());
        assertTrue(canceled.study().focusPrepared(), "an unspent focus survives a recall or cancellation");
        assertEquals(700, canceled.study().studyCooldownTicks());
        assertEquals(Mode.IDLE, canceled.mode());
    }

    @Test
    void anExpiredThreatStaysObservableSoTickDispatchCanEndThePhaseExactlyOnce() {
        // The canonical constructor used to release the threat the instant its window reached
        // zero, which made the transition invisible and stranded the one-hop report marker.
        // Phase ending now belongs to tick dispatch alone, so a zero-tick threat survives until
        // CircleMageRuntime.endExpiredPhases releases it, counts it, and clears the marker.
        final CircleMageState.Threat expiring = new CircleMageState.Threat(
            Optional.of(UUID.randomUUID()), Optional.of(HERE), TargetSource.PEER_REPORT, 1);
        assertTrue(expiring.present());
        final CircleMageState.Threat expired = new CircleMageState.Threat(
            expiring.id(), expiring.dimension(), expiring.source(),
            CircleMageRules.decrementLoaded(expiring.remainingTicks()));
        assertTrue(expired.present(), "the expiry transition stays observable");
        assertEquals(0, expired.remainingTicks());
        assertEquals(TargetSource.PEER_REPORT, expired.source(),
            "the runtime can still see which motive is ending");
    }

    @Test
    void anExpiredSessionLikewiseSurvivesUntilTickDispatchReleasesIt() {
        final CircleMageState.Session ending = CircleMageState.Session.joined(
            UUID.randomUUID(), HERE, 5L, 1);
        final CircleMageState.Session expired = new CircleMageState.Session(
            ending.coordinator(), ending.dimension(), ending.epoch(), ending.slot(), 0);
        assertTrue(expired.present(), "so the timeout release can be counted exactly once");
        assertEquals(0, expired.remainingTicks());
        assertEquals(ending.coordinator(), expired.coordinator());
    }

    @Test
    void structuralCouplingIsStillValidatedEvenThoughTimersAreNot() {
        // Removing the timer reconciliation must not weaken malformed-data validation.
        assertFalse(new CircleMageState.Threat(
            Optional.of(UUID.randomUUID()), Optional.empty(), TargetSource.DIRECT, 80).present());
        assertFalse(new CircleMageState.Session(
            Optional.of(UUID.randomUUID()), Optional.empty(), 5L, 1, 100).present());
        assertEquals(Action.NONE, new CircleMageState.ActionState(
            Action.BOLT, Optional.empty(), Optional.of(HERE), true, 12).action());
    }

    @Test
    void theStateExposesNoCollectionPathOrLiveReferenceComponent() {
        final List<Class<?>> types = java.util.stream.Stream.of(
                CircleMageState.class,
                CircleMageState.Anchor.class,
                CircleMageState.Threat.class,
                CircleMageState.ActionState.class,
                CircleMageState.Study.class,
                CircleMageState.Session.class,
                CircleMageState.Cadence.class)
            .flatMap(record -> java.util.Arrays.stream(record.getRecordComponents()))
            .map(java.lang.reflect.RecordComponent::getType)
            .toList();
        assertTrue(types.stream().noneMatch(type ->
            java.util.Collection.class.isAssignableFrom(type)
                || java.util.Map.class.isAssignableFrom(type)
                || net.minecraft.world.entity.Entity.class.isAssignableFrom(type)
                || net.minecraft.world.level.Level.class.isAssignableFrom(type)
                || net.minecraft.world.level.pathfinder.Path.class.isAssignableFrom(type)));
    }

    private static int encodedBytes(final CompoundTag tag) {
        final java.io.ByteArrayOutputStream bytes = new java.io.ByteArrayOutputStream();
        try {
            net.minecraft.nbt.NbtIo.write(tag, new java.io.DataOutputStream(bytes));
        } catch (final java.io.IOException exception) {
            throw new java.io.UncheckedIOException(exception);
        }
        return bytes.size();
    }
}
