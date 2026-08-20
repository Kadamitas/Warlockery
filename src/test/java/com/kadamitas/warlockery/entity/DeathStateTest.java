package com.kadamitas.warlockery.entity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.kadamitas.warlockery.entity.DeathRules.Phase;
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

final class DeathStateTest {
    private static final String HERE = "minecraft:overworld";
    private static final String ELSEWHERE = "minecraft:the_nether";
    private static final UUID SUBJECT = new UUID(4L, 7L);

    private static DeathState appointed() {
        return DeathState.empty()
            .withAppointment(DeathState.Appointment.appointed(SUBJECT, HERE))
            .withPhase(Phase.APPROACH);
    }

    @Test
    void anEmptyStateIsAQuiescentDeathWithNoAppointment() {
        final DeathState empty = DeathState.empty();
        assertEquals(DeathState.SCHEMA_VERSION, empty.schemaVersion());
        assertEquals(Phase.QUIESCENT, empty.phase());
        assertFalse(empty.appointment().present());
        assertFalse(empty.appointment().reaped());
        assertEquals(0, empty.cadence().routeFailures());
    }

    @Test
    void anAppointmentPhaseCannotSurviveWithoutItsSubject() {
        assertEquals(Phase.QUIESCENT, DeathState.empty().withPhase(Phase.TELEGRAPH).phase());
        assertEquals(Phase.QUIESCENT, DeathState.empty().withPhase(Phase.REAP).phase());
        assertEquals(Phase.APPROACH, appointed().phase());
        assertEquals(Phase.RECOVER, DeathState.empty().withPhase(Phase.RECOVER).phase(),
            "an exhausted recovery survives this constructor: only the runtime's own recovery "
                + "decision may end it, because that is what counts the release and starts the backoff");
    }

    @Test
    void anExhaustedRecoveryIsNeverSilentlyConvertedIntoASettledRelease() {
        DeathState state = appointed().withAppointment(new DeathState.Appointment(
            Optional.of(SUBJECT), Optional.of(HERE), Optional.empty(), 200, 0, 1, true
        )).withPhase(Phase.RECOVER);
        assertEquals(Phase.RECOVER, state.phase());
        state = state.withAppointment(new DeathState.Appointment(
            state.appointment().subject(), state.appointment().dimension(),
            state.appointment().lastSeen(), 200, 0,
            DeathRules.decrementLoaded(state.appointment().recoverRemainingTicks()), true
        ));
        assertEquals(0, state.appointment().recoverRemainingTicks());
        assertEquals(Phase.RECOVER, state.phase(),
            "decrementing the last recovery tick must not steal the transition from the runtime");
        assertTrue(state.appointment().present(),
            "the subject is still held, so the runtime's release path is the one that clears it");
        final DeathState released = state.releaseAppointment();
        assertEquals(DeathRules.REAPPOINT_COOLDOWN_TICKS, released.cadence().reappointCooldownTicks(),
            "that release is what actually starts the two hundred tick backoff");
        assertEquals(Phase.RELEASE, released.phase());
    }

    @Test
    void retaliationUsesItsOwnShorterLeashRatherThanTheAppointmentDeadline() {
        final DeathState.Appointment retaliation = DeathState.Appointment.retaliation(SUBJECT, HERE);
        assertEquals(DeathRules.DIRECT_ATTACKER_TICKS, retaliation.approachRemainingTicks());
        assertEquals(DeathRules.TELEGRAPH_TICKS, retaliation.telegraphRemainingTicks());
        assertFalse(retaliation.reaped());
        assertTrue(DeathRules.DIRECT_ATTACKER_TICKS < DeathRules.APPROACH_DEADLINE_TICKS,
            "answering an attacker is deliberately a shorter leash than keeping an appointment");
    }

    @Test
    void anyEncodablePositionSurvivesTheRoundTrip() {
        final BlockPos extreme = BlockPos.of(Long.MIN_VALUE);
        final DeathState state = appointed().withAppointment(new DeathState.Appointment(
            Optional.of(SUBJECT), Optional.of(HERE), Optional.of(extreme), 100, 40, 0, false
        ));
        assertEquals(extreme, DeathState.read(state.write(), HERE).appointment().lastSeen().orElseThrow(),
            "key presence decides absence, so no encodable position reads back as empty");
        assertTrue(DeathState.read(
            appointed().write(), HERE).appointment().lastSeen().isEmpty(),
            "an unwritten position is still absent");
    }

    @Test
    void everyDurationAndCounterIsClamped() {
        final DeathState state = appointed().withAppointment(new DeathState.Appointment(
            Optional.of(SUBJECT), Optional.of(HERE), Optional.of(BlockPos.ZERO),
            999_999, -4, 999_999, false
        )).withCadence(new DeathState.Cadence(-1, 99, 999_999));
        assertEquals(DeathRules.APPROACH_DEADLINE_TICKS, state.appointment().approachRemainingTicks());
        assertEquals(0, state.appointment().telegraphRemainingTicks());
        assertEquals(DeathRules.RECOVER_TICKS, state.appointment().recoverRemainingTicks());
        assertEquals(0, state.cadence().reappointCooldownTicks());
        assertEquals(DeathRules.MAX_ROUTE_FAILURES, state.cadence().routeFailures());
        assertEquals(DeathRules.ROUTE_BACKOFF_TICKS, state.cadence().routeRetryTicks());
    }

    @Test
    void anAbsentSubjectErasesEveryEpisodeFact() {
        final DeathState.Appointment appointment = new DeathState.Appointment(
            Optional.empty(), Optional.of(HERE), Optional.of(BlockPos.ZERO), 300, 40, 0, true
        );
        assertFalse(appointment.present());
        assertTrue(appointment.dimension().isEmpty());
        assertTrue(appointment.lastSeen().isEmpty());
        assertEquals(0, appointment.approachRemainingTicks());
        assertEquals(0, appointment.telegraphRemainingTicks());
        assertFalse(appointment.reaped());
    }

    @Test
    void releaseClearsTheSubjectResetsFailuresAndStartsTheBackoff() {
        final DeathState released = appointed()
            .withCadence(new DeathState.Cadence(0, DeathRules.MAX_ROUTE_FAILURES, 0))
            .releaseAppointment();
        assertFalse(released.appointment().present());
        assertEquals(0, released.cadence().routeFailures());
        assertEquals(DeathRules.REAPPOINT_COOLDOWN_TICKS, released.cadence().reappointCooldownTicks());
        assertEquals(Phase.RELEASE, released.phase());
    }

    @Test
    void aReleaseDuringRecoveryKeepsTheRecoveryOwing() {
        final DeathState recovering = appointed().withAppointment(new DeathState.Appointment(
            Optional.of(SUBJECT), Optional.of(HERE), Optional.empty(), 200, 0,
            DeathRules.RECOVER_TICKS, true
        )).withPhase(Phase.RECOVER).releaseAppointment();
        assertEquals(Phase.RECOVER, recovering.phase());
        assertEquals(DeathRules.RECOVER_TICKS, recovering.appointment().recoverRemainingTicks());
        assertFalse(recovering.appointment().present(),
            "the subject identity is gone even while the recovery still owes ticks");
    }

    @Test
    void roundTripPreservesEverySemanticFact() {
        final DeathState original = appointed().withAppointment(new DeathState.Appointment(
            Optional.of(SUBJECT), Optional.of(HERE), Optional.of(new BlockPos(3, 64, -9)),
            123, 17, 0, false
        )).withCadence(new DeathState.Cadence(40, 2, 30));
        final DeathState restored = DeathState.read(original.write(), HERE);
        assertEquals(SUBJECT, restored.appointment().subject().orElseThrow());
        assertEquals(new BlockPos(3, 64, -9), restored.appointment().lastSeen().orElseThrow());
        assertEquals(123, restored.appointment().approachRemainingTicks());
        assertEquals(17, restored.appointment().telegraphRemainingTicks());
        assertEquals(40, restored.cadence().reappointCooldownTicks());
        assertEquals(2, restored.cadence().routeFailures());
        assertEquals(30, restored.cadence().routeRetryTicks());
    }

    @Test
    void reloadNeverRepeatsACompletedReap() {
        final DeathState afterReap = appointed().withAppointment(new DeathState.Appointment(
            Optional.of(SUBJECT), Optional.of(HERE), Optional.empty(), 200, 0,
            DeathRules.RECOVER_TICKS, true
        )).withPhase(Phase.RECOVER);
        final DeathState restored = DeathState.read(afterReap.write(), HERE);
        assertTrue(restored.appointment().reaped(), "a completed attempt survives the reload");
        assertFalse(DeathRules.reapAllowed(true, true, 0, restored.appointment().reaped()),
            "the restored episode can never call the melee path again");
        assertEquals(Phase.RECOVER, restored.phase());
    }

    @Test
    void aStoredReapPhaseAlwaysResumesAsACompletedAttempt() {
        final CompoundTag tag = appointed().withPhase(Phase.APPROACH).write();
        tag.putString("Phase", "reap");
        final DeathState restored = DeathState.read(tag, HERE);
        assertEquals(Phase.APPROACH, restored.phase(),
            "reload reconstructs a plain approach, never a mid-hold telegraph or a reaping call");
        assertTrue(restored.appointment().reaped());
    }

    @Test
    void aStoredTelegraphResumesAsAnApproachWithAFullTelegraphStillOwed() {
        final DeathState telegraphing = appointed().withPhase(Phase.TELEGRAPH);
        final DeathState restored = DeathState.read(telegraphing.write(), HERE);
        assertEquals(Phase.APPROACH, restored.phase());
        assertFalse(restored.appointment().reaped());
        assertEquals(DeathRules.TELEGRAPH_TICKS, restored.appointment().telegraphRemainingTicks());
    }

    @Test
    void aSubjectRecordedInAnotherDimensionIsDropped() {
        final DeathState restored = DeathState.read(appointed().write(), ELSEWHERE);
        assertFalse(restored.appointment().present());
        assertEquals(Phase.QUIESCENT, restored.phase());
    }

    @Test
    void malformedDefaultAndUnknownSchemaAllResetSafely() {
        assertEquals(DeathState.empty(), DeathState.read(null, HERE));
        assertEquals(DeathState.empty(), DeathState.read(new CompoundTag(), HERE));
        final CompoundTag future = appointed().write();
        future.putInt("Version", DeathState.SCHEMA_VERSION + 1);
        assertEquals(DeathState.empty(), DeathState.read(future, HERE));
        final CompoundTag malformed = appointed().write();
        malformed.putString("SubjId", "not-a-uuid");
        malformed.putString("Phase", "not-a-phase");
        final DeathState restored = DeathState.read(malformed, HERE);
        assertFalse(restored.appointment().present());
        assertEquals(Phase.QUIESCENT, restored.phase());
    }

    @Test
    void aPopulatedStateStaysBelowTheDeclaredByteCeiling() {
        final DeathState populated = appointed().withAppointment(new DeathState.Appointment(
            Optional.of(SUBJECT), Optional.of(HERE), Optional.of(new BlockPos(-100, 200, 300)),
            DeathRules.APPROACH_DEADLINE_TICKS, DeathRules.TELEGRAPH_TICKS,
            DeathRules.RECOVER_TICKS, true
        )).withCadence(new DeathState.Cadence(
            DeathRules.REAPPOINT_COOLDOWN_TICKS, DeathRules.MAX_ROUTE_FAILURES,
            DeathRules.ROUTE_BACKOFF_TICKS
        ));
        assertTrue(encode(populated.write()).length < DeathRules.MAX_STATE_BYTES);
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
