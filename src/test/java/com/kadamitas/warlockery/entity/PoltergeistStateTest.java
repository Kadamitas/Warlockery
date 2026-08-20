package com.kadamitas.warlockery.entity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.kadamitas.warlockery.entity.PoltergeistRules.Phase;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Locale;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtIo;
import org.junit.jupiter.api.Test;

/** Persistence, clamping and reload contracts for the Poltergeist semantic state. */
final class PoltergeistStateTest {
    private static PoltergeistState populated() {
        return PoltergeistState.empty()
            .withEpisode(new PoltergeistState.Episode(150, 30, 5, 2, 1, 1, 1, 1, 0, 4))
            .withCadence(new PoltergeistState.Cadence(0, 2, 30))
            .withPhase(Phase.THROW);
    }

    @Test
    void anEmptyStateIsAVersionedLurkWithNothingHeld() {
        final PoltergeistState empty = PoltergeistState.empty();
        assertEquals(PoltergeistState.SCHEMA_VERSION, empty.schemaVersion());
        assertEquals(Phase.LURK, empty.phase());
        assertFalse(empty.episode().active());
        assertEquals(0, empty.episode().lifts());
        assertEquals(0, empty.cadence().cooldownTicks());
        assertEquals(0, empty.cadence().routeFailures());
    }

    @Test
    void everyStoredDurationAndCountIsClampedIndependently() {
        final PoltergeistState.Episode episode = new PoltergeistState.Episode(
            Integer.MAX_VALUE, -40, 9_999, 99, 7, 5, 3, 8, 6, 400
        );
        assertEquals(PoltergeistRules.EPISODE_TICKS, episode.remainingTicks());
        assertEquals(0, episode.phaseRemainingTicks());
        assertEquals(PoltergeistRules.RATTLE_PULSE_INTERVAL_TICKS, episode.pulseRemainingTicks());
        assertEquals(PoltergeistRules.MAX_RATTLE_PULSES, episode.pulsesEmitted());
        assertEquals(PoltergeistRules.MAX_BELL_RINGS, episode.bellRings());
        assertEquals(PoltergeistRules.MAX_LIFTS, episode.lifts());
        assertEquals(PoltergeistRules.MAX_VELOCITY_WRITES, episode.velocityWrites());
        assertEquals(PoltergeistRules.MAX_THROW_HITS, episode.hits());
        assertEquals(PoltergeistRules.MAX_RECOVERIES, episode.recoveries());
        assertEquals(PoltergeistRules.MAX_EPISODE_PATH_REQUESTS, episode.pathRequests());

        final PoltergeistState.Cadence cadence =
            new PoltergeistState.Cadence(-9, 42, Integer.MAX_VALUE);
        assertEquals(0, cadence.cooldownTicks());
        assertEquals(PoltergeistRules.MAX_ROUTE_FAILURES, cadence.routeFailures());
        assertEquals(PoltergeistRules.ROUTE_BACKOFF_TICKS, cadence.routeRetryTicks());
    }

    /**
     * The defect-class-two guard. The canonical constructor must clamp ranges only. A timer-shaped
     * reconcile here would end the episode itself, stealing the recovery branch's single exit and
     * erasing the spent-work evidence the surviving recovery still has to observe.
     */
    @Test
    void anExhaustedEpisodeBudgetNeverErasesTheSpentWorkTheRecoveryStillObserves() {
        final PoltergeistState.Episode expired =
            new PoltergeistState.Episode(0, 12, 0, 3, 1, 1, 1, 1, 0, 3);
        assertFalse(expired.active());
        assertEquals(12, expired.phaseRemainingTicks(),
            "the phase window survives an exhausted episode budget");
        assertEquals(1, expired.lifts(), "the spent lift is still observable");
        assertEquals(1, expired.velocityWrites(), "the spent velocity write is still observable");
        assertEquals(1, expired.hits(), "the spent hit is still observable");
        assertEquals(1, expired.bellRings(), "the spent bell ring is still observable");
        assertEquals(0, expired.recoveries(),
            "the owed recovery has not been granted by the constructor");
        assertEquals(3, expired.pathRequests(), "the spent path quota is still observable");
    }

    @Test
    void enteringRecoveryNeverArmsTheCooldownAndFinishingAlwaysDoes() {
        final PoltergeistState cancelled = populated().enterRecovery();
        assertEquals(Phase.RECOVER, cancelled.phase());
        assertEquals(PoltergeistRules.RECOVER_TICKS, cancelled.episode().phaseRemainingTicks());
        assertEquals(0, cancelled.cadence().cooldownTicks(),
            "the cooldown belongs to the recovery exit, never to entering the recovery");
        assertEquals(1, cancelled.episode().lifts(),
            "cancelling preserves the spent work so no second lift is granted");

        final PoltergeistState finished = cancelled.finishEpisode();
        assertEquals(Phase.LURK, finished.phase());
        assertEquals(PoltergeistRules.COOLDOWN_TICKS, finished.cadence().cooldownTicks());
        assertEquals(0, finished.cadence().routeFailures());
        assertFalse(finished.episode().active());
        assertEquals(0, finished.episode().lifts());
        assertEquals(0, finished.episode().pathRequests());
    }

    @Test
    void everyEpisodeFieldHasAnIndependentWriter() {
        final PoltergeistState.Episode base = PoltergeistState.Episode.started();
        assertEquals(PoltergeistRules.EPISODE_TICKS, base.remainingTicks());
        assertEquals(PoltergeistRules.RATTLE_TICKS, base.phaseRemainingTicks());
        assertEquals(0, base.pulseRemainingTicks(), "the first rattle pulse is due immediately");
        assertEquals(5, base.withRemaining(5).remainingTicks());
        assertEquals(7, base.withPhaseRemaining(7).phaseRemainingTicks());
        assertEquals(3, base.withPulse(3, 1).pulseRemainingTicks());
        assertEquals(1, base.withPulse(3, 1).pulsesEmitted());
        assertEquals(1, base.withBellRings(1).bellRings());
        assertEquals(1, base.withLifts(1).lifts());
        assertEquals(1, base.withVelocityWrites(1).velocityWrites());
        assertEquals(1, base.withHits(1).hits());
        assertEquals(1, base.withRecoveries(1).recoveries());
        assertEquals(3, base.withPathRequests(3).pathRequests());
        assertEquals(PoltergeistRules.EPISODE_TICKS, base.withLifts(1).remainingTicks(),
            "writing one field never disturbs another");
    }

    @Test
    void aRoundTripPreservesEveryLurkFactExactly() {
        final PoltergeistState lurking = PoltergeistState.empty()
            .withCadence(new PoltergeistState.Cadence(500, 1, 20));
        final PoltergeistState restored = PoltergeistState.read(lurking.write());
        assertEquals(lurking, restored);
    }

    @Test
    void aSavedAttackPhaseResumesAsARecoveryThatCannotReplayItsSpentWork() {
        final PoltergeistState restored = PoltergeistState.read(populated().write());
        assertEquals(Phase.RECOVER, restored.phase(),
            "no reload ever lands inside an open lift or throw window");
        assertEquals(PoltergeistRules.RECOVER_TICKS, restored.episode().phaseRemainingTicks(),
            "the resumed recovery is armed with a real window rather than a zero one");
        assertEquals(1, restored.episode().lifts(),
            "the spent lift survives so no second lift is granted");
        assertEquals(1, restored.episode().velocityWrites(),
            "the spent velocity write survives so no delayed throw replays");
        assertEquals(1, restored.episode().hits(), "the spent hit survives");
        assertEquals(1, restored.episode().bellRings(), "the spent bell ring survives");
        assertEquals(4, restored.episode().pathRequests(), "the spent path quota survives");
        assertEquals(0, restored.cadence().cooldownTicks(),
            "the cooldown is still owed to the recovery tick branch, not granted by the load");
    }

    @Test
    void aSavedRecoveryKeepsItsOwnRemainingWindow() {
        final PoltergeistState saved = populated()
            .withEpisode(populated().episode().withPhaseRemaining(9))
            .withPhase(Phase.RECOVER);
        assertEquals(9, PoltergeistState.read(saved.write()).episode().phaseRemainingTicks());
    }

    @Test
    void anUnknownSchemaOrGarbledPhaseResetsToASafeLurk() {
        final CompoundTag future = populated().write();
        future.putInt("Version", PoltergeistState.SCHEMA_VERSION + 7);
        assertEquals(PoltergeistState.empty(), PoltergeistState.read(future));
        assertEquals(PoltergeistState.empty(), PoltergeistState.read(null));

        final CompoundTag garbled = populated().write();
        garbled.putString("Phase", "not_a_phase");
        assertEquals(Phase.LURK, PoltergeistState.read(garbled).phase());
    }

    @Test
    void noTargetPropOrOwnerIdentityIsEverWrittenIntoTheSpeciesState() {
        final CompoundTag encoded = populated().write();
        assertTrue(encoded.keySet().stream()
            .map(key -> key.toLowerCase(Locale.ROOT))
            .noneMatch(key -> key.contains("owner") || key.contains("target")
                || key.contains("prop") || key.contains("uuid")),
            "the marked target and the claimed prop are transient by design");
    }

    @Test
    void aRepresentativePopulatedStateEncodesBelowTheDeclaredByteCeiling() {
        assertTrue(encode(populated().write()).length < PoltergeistRules.MAX_STATE_BYTES);
        assertTrue(encode(PoltergeistState.empty().write()).length
            < PoltergeistRules.MAX_STATE_BYTES);
    }

    private static byte[] encode(final CompoundTag tag) {
        final ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try {
            NbtIo.write(tag, new DataOutputStream(bytes));
        } catch (final IOException failure) {
            throw new UncheckedIOException(failure);
        }
        return bytes.toByteArray();
    }
}
