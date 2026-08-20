package com.kadamitas.warlockery.entity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.kadamitas.warlockery.entity.SpiritRules.Phase;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtIo;
import org.junit.jupiter.api.Test;

/** Persistence, clamping and reconciliation contracts for the Spirit semantic state. */
final class SpiritStateTest {
    private static final String HERE = "minecraft:overworld";
    private static final String ELSEWHERE = "minecraft:the_end";
    private static final UUID ATTACKER = UUID.fromString("00000000-0000-0000-0000-0000000000ff");

    private static SpiritState guarding() {
        return SpiritState.empty()
            .withGuard(SpiritState.Guard.warning(ATTACKER, HERE, 0))
            .withPhase(Phase.WARN);
    }

    private static SpiritState attending() {
        return SpiritState.empty()
            .withAnchor(SpiritState.Anchor.at(new BlockPos(-8, 70, 4), HERE))
            .withAttendance(new SpiritState.Attendance(150, 30, 1))
            .withCadence(new SpiritState.Cadence(1, 20, 0))
            .withPhase(Phase.ATTEND);
    }

    @Test
    void anEmptyStateIsAVersionedWanderWithNothingHeld() {
        final SpiritState empty = SpiritState.empty();
        assertEquals(SpiritState.SCHEMA_VERSION, empty.schemaVersion());
        assertEquals(Phase.WANDER, empty.phase());
        assertFalse(empty.anchor().present());
        assertFalse(empty.wary().active());
        assertFalse(empty.attendance().active());
        assertFalse(empty.guard().present());
    }

    @Test
    void everyStoredDurationAndCountIsClamped() {
        final SpiritState.Wary wary = new SpiritState.Wary(Integer.MAX_VALUE, -3);
        assertEquals(SpiritRules.WARY_TICKS, wary.remainingTicks());
        assertEquals(0, wary.cooldownTicks());

        final SpiritState.Attendance attendance =
            new SpiritState.Attendance(Integer.MAX_VALUE, Integer.MAX_VALUE, 9);
        assertEquals(SpiritRules.ATTEND_TICKS, attendance.remainingTicks());
        assertEquals(SpiritRules.ATTEND_PULSE_INTERVAL_TICKS, attendance.pulseRemainingTicks());
        assertEquals(SpiritRules.MAX_ATTEND_PULSES, attendance.pulsesEmitted());

        final SpiritState.Guard guard = new SpiritState.Guard(
            Optional.of(ATTACKER), Optional.of(HERE), -5, Integer.MAX_VALUE, 99,
            Integer.MAX_VALUE, 7, Integer.MAX_VALUE
        );
        assertEquals(0, guard.warnRemainingTicks());
        assertEquals(SpiritRules.WARN_PULSE_INTERVAL_TICKS, guard.warnPulseRemainingTicks());
        assertEquals(SpiritRules.MAX_WARN_PULSES, guard.warnPulsesEmitted());
        assertEquals(SpiritRules.DEFEND_TICKS, guard.defendRemainingTicks());
        assertEquals(SpiritRules.MAX_DEFENCE_STRIKES, guard.strikes(),
            "a corrupt strike count can never buy a second attributed attack");
        assertEquals(SpiritRules.RECOVER_TICKS, guard.recoverRemainingTicks());

        final SpiritState.Cadence cadence = new SpiritState.Cadence(42, -1, Integer.MAX_VALUE);
        assertEquals(SpiritRules.MAX_ROUTE_FAILURES, cadence.routeFailures());
        assertEquals(0, cadence.routeRetryTicks());
        assertEquals(SpiritRules.ATTEND_COOLDOWN_TICKS, cadence.attendCooldownTicks());
    }

    @Test
    void aHalfPopulatedGuardCollapsesButKeepsOnlyItsRecoveryWindow() {
        final SpiritState.Guard orphan = new SpiritState.Guard(
            Optional.of(ATTACKER), Optional.empty(), 30, 10, 1, 20, 1, 50
        );
        assertFalse(orphan.present());
        assertEquals(0, orphan.warnRemainingTicks());
        assertEquals(0, orphan.defendRemainingTicks());
        assertEquals(0, orphan.strikes());
        assertEquals(50, orphan.recoverRemainingTicks(),
            "the recovery window outlives the attacker it was earned against");
    }

    @Test
    void theCanonicalConstructorNeverRewritesAPhaseATickBranchIsResponsibleForEnding() {
        // Regression: reconciling an elapsed wary reaction or attendance away in the constructor
        // meant tickWary and tickAttend never ran their timeout branches, so neither cooldown was
        // ever armed and a cornered Spirit fled forever.
        for (final Phase phase : Phase.values()) {
            assertEquals(phase, SpiritState.empty().withPhase(phase).phase(),
                phase + " must survive into the tick that owns ending it");
        }
        assertEquals(Phase.WARY, SpiritState.empty()
            .withWary(new SpiritState.Wary(0, 0)).withPhase(Phase.WARY).phase(),
            "an elapsed wary reaction stays WARY so tickWary can arm its cooldown");
        assertEquals(Phase.ATTEND, SpiritState.empty()
            .withAttendance(new SpiritState.Attendance(0, 0, 0)).withPhase(Phase.ATTEND).phase(),
            "an elapsed attendance stays ATTEND so tickAttend can arm its cooldown");
        assertEquals(Phase.WARN, guarding().phase());
        assertEquals(Phase.ATTEND, attending().phase());
    }

    @Test
    void bindingAtomicallyStopsAvoidanceAttendanceAndAnyGuard() {
        final SpiritState bound = attending()
            .withWary(SpiritState.Wary.started())
            .withGuard(SpiritState.Guard.warning(ATTACKER, HERE, 0))
            .bind();
        assertEquals(Phase.BOUND, bound.phase());
        assertFalse(bound.wary().active(), "no wary reaction survives a binding");
        assertFalse(bound.attendance().active());
        assertFalse(bound.anchor().present());
        assertFalse(bound.guard().present());
        assertEquals(0, bound.cadence().routeFailures());
        assertEquals(0, bound.cadence().attendCooldownTicks());
    }

    @Test
    void endingAGuardKeepsOnlyTheRecoveryWindowThatForbidsASecondDefence() {
        final SpiritState recovered = guarding()
            .withGuard(new SpiritState.Guard(Optional.of(ATTACKER), Optional.of(HERE),
                0, 0, 2, 10, 1, 0))
            .endGuard();
        assertEquals(Phase.RECOVER, recovered.phase());
        assertFalse(recovered.guard().present());
        assertEquals(0, recovered.guard().strikes());
        assertEquals(SpiritRules.RECOVER_TICKS, recovered.guard().recoverRemainingTicks());
        assertFalse(SpiritRules.guardAllowed(true, true,
            recovered.guard().recoverRemainingTicks()));
    }

    @Test
    void normalizingABoundPhaseMustNeverClearAnEarnedRecoveryWindow() {
        // The bound follow path normalizes an idle phase to BOUND. Doing that through bind() would
        // reset the guard, handing back a second attributed strike inside the recovery window.
        final SpiritState recovering = SpiritState.empty()
            .withGuard(SpiritState.Guard.recovering(SpiritRules.RECOVER_TICKS))
            .withPhase(Phase.RECOVER);
        assertEquals(SpiritRules.RECOVER_TICKS,
            recovering.withPhase(Phase.BOUND).guard().recoverRemainingTicks(),
            "withPhase carries the recovery window; only an explicit bind() may drop it");
        assertEquals(0, recovering.bind().guard().recoverRemainingTicks());
        assertFalse(SpiritRules.guardAllowed(true, true,
            recovering.withPhase(Phase.BOUND).guard().recoverRemainingTicks()));
    }

    @Test
    void endingAttendanceStartsItsOwnCooldown() {
        final SpiritState ended = attending().endAttendance();
        assertEquals(Phase.WANDER, ended.phase());
        assertFalse(ended.anchor().present());
        assertFalse(ended.attendance().active());
        assertEquals(SpiritRules.ATTEND_COOLDOWN_TICKS, ended.cadence().attendCooldownTicks());
    }

    @Test
    void aRoundTripPreservesEverySemanticFact() {
        final SpiritState original = attending();
        final SpiritState restored = SpiritState.read(original.write(), HERE);
        assertEquals(original.anchor(), restored.anchor());
        assertEquals(original.attendance().remainingTicks(), restored.attendance().remainingTicks());
        assertEquals(original.attendance().pulsesEmitted(), restored.attendance().pulsesEmitted());
        assertEquals(original.cadence().routeFailures(), restored.cadence().routeFailures());
        assertEquals(Phase.ATTEND, restored.phase());
    }

    @Test
    void aPersistedDefenceReloadsAsAWarningAndNeverInsideAStrikeWindow() {
        final SpiritState defending = guarding()
            .withGuard(new SpiritState.Guard(Optional.of(ATTACKER), Optional.of(HERE),
                0, 0, 2, SpiritRules.DEFEND_TICKS, 0, 0))
            .withPhase(Phase.DEFEND);
        final SpiritState restored = SpiritState.read(defending.write(), HERE);
        assertEquals(Phase.WARN, restored.phase());
        assertEquals(0, restored.guard().strikes());
    }

    @Test
    void aPersistedStrikeSurvivesSoNoReloadGrantsASecondAttack() {
        final SpiritState struck = guarding()
            .withGuard(new SpiritState.Guard(Optional.of(ATTACKER), Optional.of(HERE),
                0, 0, 2, 10, 1, 0));
        final SpiritState restored = SpiritState.read(struck.write(), HERE);
        assertEquals(1, restored.guard().strikes());
        assertEquals(SpiritRules.GuardEnd.STRUCK, SpiritRules.guardEnd(
            new SpiritRules.GuardObservation(true, true, true, true, false, 1.0D,
                restored.guard().strikes(), 10, 0)));
    }

    @Test
    void aGuardSubjectFromAnotherDimensionIsDropped() {
        final SpiritState restored = SpiritState.read(guarding().write(), ELSEWHERE);
        assertFalse(restored.guard().present(),
            "a guard subject never survives into another dimension");
        assertEquals(0, restored.guard().strikes());
        assertFalse(SpiritState.read(attending().write(), ELSEWHERE).anchor().present());
    }

    @Test
    void aReloadNeverReplaysAWarningOrAttendancePulse() {
        final SpiritState dueWarn = guarding()
            .withGuard(new SpiritState.Guard(Optional.of(ATTACKER), Optional.of(HERE),
                20, 0, 1, 0, 0, 0));
        assertEquals(SpiritRules.WARN_PULSE_INTERVAL_TICKS,
            SpiritState.read(dueWarn.write(), HERE).guard().warnPulseRemainingTicks());
        final SpiritState dueAttend = attending()
            .withAttendance(new SpiritState.Attendance(150, 0, 1));
        assertEquals(SpiritRules.ATTEND_PULSE_INTERVAL_TICKS,
            SpiritState.read(dueAttend.write(), HERE).attendance().pulseRemainingTicks());
    }

    @Test
    void malformedAndUnknownSchemasResetToASafeWander() {
        assertEquals(SpiritState.empty(), SpiritState.read(null, HERE));
        assertEquals(SpiritState.empty(), SpiritState.read(new CompoundTag(), HERE));
        final CompoundTag future = attending().write();
        future.putInt("Version", SpiritState.SCHEMA_VERSION + 3);
        assertEquals(SpiritState.empty(), SpiritState.read(future, HERE));
        final CompoundTag garbledId = guarding().write();
        garbledId.putString("AtkId", "not-a-uuid");
        assertFalse(SpiritState.read(garbledId, HERE).guard().present());
        final CompoundTag garbledPhase = attending().write();
        garbledPhase.putString("Phase", "screeching");
        assertEquals(Phase.WANDER, SpiritState.read(garbledPhase, HERE).phase());
    }

    @Test
    void aRepresentativePopulatedStateEncodesBelowTheDeclaredByteCeiling() {
        assertTrue(encode(attending().write()).length < SpiritRules.MAX_STATE_BYTES);
        assertTrue(encode(guarding().write()).length < SpiritRules.MAX_STATE_BYTES);
        assertTrue(encode(SpiritState.empty().write()).length < SpiritRules.MAX_STATE_BYTES);
    }

    @Test
    void noOwnerIdentityIsEverWrittenIntoTheSpeciesState() {
        final CompoundTag encoded = attending().bind().write();
        assertFalse(encoded.keySet().stream()
            .anyMatch(key -> key.toLowerCase(java.util.Locale.ROOT).contains("owner")),
            "the generic CreatureBehaviorState UUID stays the one owner authority");
    }

    private static byte[] encode(final CompoundTag tag) {
        final ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try {
            NbtIo.write(tag, new DataOutputStream(bytes));
        } catch (final IOException exception) {
            throw new UncheckedIOException(exception);
        }
        return bytes.toByteArray();
    }
}
