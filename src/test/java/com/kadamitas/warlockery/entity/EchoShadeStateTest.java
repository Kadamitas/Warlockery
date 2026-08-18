package com.kadamitas.warlockery.entity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.kadamitas.warlockery.entity.ApparitionEpisodeRules.RouteLedger;
import com.kadamitas.warlockery.entity.EchoShadeRules.Phase;
import com.kadamitas.warlockery.entity.EchoShadeState.Echo;
import com.kadamitas.warlockery.entity.EchoShadeState.Mark;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtIo;
import org.junit.jupiter.api.Test;

/** Persistence, clamping and reconciliation contracts for the F21 Echo Shade state record. */
final class EchoShadeStateTest {
    private static final String OVERWORLD = "minecraft:overworld";
    private static final String NETHER = "minecraft:the_nether";
    private static final UUID MARK = UUID.fromString("00000000-0000-0000-0000-0000000000aa");

    @Test
    void anEmptyStateIsAQuietWatchWithNothingClaimed() {
        final EchoShadeState empty = EchoShadeState.empty();
        assertEquals(EchoShadeState.SCHEMA_VERSION, empty.schemaVersion());
        assertEquals(Phase.WATCH, empty.phase());
        assertFalse(empty.mark().present());
        assertFalse(empty.echo().active());
        assertEquals(0, empty.cooldownTicks());
        assertEquals(0, empty.route().routeFailures());
    }

    // ------------------------------------------------------------ reconciliation shape

    /**
     * The identity shape of reconciliation: the two halves of one mark cannot disagree. This is the
     * type asserting a coupled invariant, not a constructor deciding that something ended.
     */
    @Test
    void aHalfWrittenMarkCollapsesToNoMarkAtAll() {
        assertFalse(new Mark(Optional.of(MARK), Optional.empty()).present());
        assertFalse(new Mark(Optional.empty(), Optional.of(OVERWORLD)).present());
        assertFalse(new Mark(Optional.of(MARK), Optional.of("  ")).present(),
            "a blank dimension key is not a dimension");
        assertTrue(Mark.of(MARK, OVERWORLD).present());
    }

    /**
     * The timer shape of reconciliation is the defect, and it is absent here on purpose. An echo
     * whose total budget has run out must keep its sibling timers intact so the tick branch that
     * owns ending the current phase can still observe its own expiry and arm what it owes.
     */
    @Test
    void anExpiredEchoKeepsItsSiblingTimersSoNoTickBranchLosesItsEnding() {
        final Echo expired = new Echo(0, 0, 0, 0, 25, 0, 4_000, 0, 3, 0);
        assertFalse(expired.active(), "the total budget is genuinely spent");
        assertEquals(25, expired.strikeRemainingTicks(),
            "the strike window survives so its owning branch still runs and arms the recovery");
        assertEquals(4_000, expired.recordedMillisX(),
            "the recorded gesture is not silently discarded by the constructor");
        assertEquals(3, expired.samples());
    }

    @Test
    void aSpentStrikeCountIsNeverReconciledAwayByAnySiblingField() {
        final Echo spent = new Echo(0, 0, 0, 0, 0, 0, 0, 0, 0, EchoShadeRules.MAX_STRIKES);
        assertEquals(EchoShadeRules.MAX_STRIKES, spent.strikes(),
            "no reload or sibling timer may ever hand back the one spent attempt");
    }

    // ------------------------------------------------------------ clamping

    @Test
    void everyDurationAndCounterIsClampedIntoItsDeclaredRange() {
        final Echo wild = new Echo(9_999, 9_999, 9_999, 9_999, 9_999, 9_999,
            999_999, -999_999, 999, 999);
        assertEquals(EchoShadeRules.EPISODE_TICKS, wild.remainingTicks());
        assertEquals(EchoShadeRules.RECORD_TICKS, wild.recordRemainingTicks());
        assertEquals(EchoShadeRules.SAMPLE_INTERVAL_TICKS, wild.sampleRemainingTicks());
        assertEquals(EchoShadeRules.ANSWER_TICKS, wild.answerRemainingTicks());
        assertEquals(EchoShadeRules.STRIKE_TICKS, wild.strikeRemainingTicks());
        assertEquals(EchoShadeRules.RECOVER_TICKS, wild.recoverRemainingTicks());
        assertEquals(EchoShadeRules.MAX_RECORDED_MILLIS, wild.recordedMillisX());
        assertEquals(-EchoShadeRules.MAX_RECORDED_MILLIS, wild.recordedMillisZ());
        assertEquals(EchoShadeRules.MAX_SAMPLES, wild.samples());
        assertEquals(EchoShadeRules.MAX_STRIKES, wild.strikes());

        final Echo negative = new Echo(-1, -1, -1, -1, -1, -1, 0, 0, -1, -1);
        assertEquals(0, negative.remainingTicks());
        assertEquals(0, negative.samples());
        assertEquals(0, negative.strikes());

        assertEquals(EchoShadeRules.COOLDOWN_TICKS,
            EchoShadeState.empty().withCooldown(9_999).cooldownTicks());
    }

    @Test
    void aFreshEchoStartsWithItsRecordWindowArmedAndNothingSpent() {
        final Echo started = Echo.started();
        assertTrue(started.active());
        assertEquals(EchoShadeRules.RECORD_TICKS, started.recordRemainingTicks());
        assertEquals(EchoShadeRules.SAMPLE_INTERVAL_TICKS, started.sampleRemainingTicks());
        assertEquals(0, started.strikes());
        assertEquals(0, started.samples());
        assertEquals(0, started.recordedMillisX());
    }

    // ------------------------------------------------------------ endings

    @Test
    void endingAnEchoReleasesTheMarkAndArmsTheCadenceInOneWrite() {
        final EchoShadeState running = EchoShadeState.empty()
            .withMark(Mark.of(MARK, OVERWORLD))
            .withEcho(Echo.started())
            .withRoute(new RouteLedger(0, 2, 0))
            .withPhase(Phase.ANSWER);
        final EchoShadeState ended = running.endEcho();
        assertEquals(Phase.WATCH, ended.phase());
        assertFalse(ended.mark().present(), "no mark survives the ending");
        assertFalse(ended.echo().active(), "no echo survives the ending");
        assertEquals(0, ended.route().routeFailures(), "the failure run is cleared with the echo");
        assertEquals(EchoShadeRules.COOLDOWN_TICKS, ended.cooldownTicks(),
            "the ending arms the cadence so a second echo cannot start immediately");
    }

    // ------------------------------------------------------------ persistence

    @Test
    void aPopulatedStateRoundTripsThroughItsOwnEncoding() {
        final EchoShadeState original = new EchoShadeState(
            EchoShadeState.SCHEMA_VERSION, Phase.ANSWER, Mark.of(MARK, OVERWORLD),
            new Echo(300, 0, 0, 90, 0, 0, 5_500, -2_250, 6, 0),
            new RouteLedger(7, 2, 40), 0
        );
        final EchoShadeState restored = EchoShadeState.read(original.write(), OVERWORLD);
        assertEquals(original, restored);
    }

    @Test
    void aReloadNeverResumesInsideAnOpenStrikeWindowButKeepsTheSpentAttempt() {
        final EchoShadeState striking = new EchoShadeState(
            EchoShadeState.SCHEMA_VERSION, Phase.STRIKE, Mark.of(MARK, OVERWORLD),
            new Echo(300, 0, 0, 0, 30, 0, 5_000, 0, 6, 1),
            new RouteLedger(0, 0, 0), 0
        );
        final EchoShadeState restored = EchoShadeState.read(striking.write(), OVERWORLD);
        assertEquals(Phase.ANSWER, restored.phase(),
            "a reload demotes an open strike window so the attempt must be earned again");
        assertEquals(1, restored.echo().strikes(),
            "the spent attempt survives the reload so no second strike is granted");
    }

    @Test
    void aMarkFromAnotherDimensionIsDroppedRatherThanChased() {
        final EchoShadeState saved = EchoShadeState.empty()
            .withMark(Mark.of(MARK, OVERWORLD))
            .withEcho(Echo.started())
            .withPhase(Phase.RECORD);
        final EchoShadeState restored = EchoShadeState.read(saved.write(), NETHER);
        assertFalse(restored.mark().present(),
            "a mark in another dimension is released rather than pursued across worlds");
    }

    @Test
    void anUnknownOrMissingSchemaResetsToASafeWatch() {
        assertEquals(EchoShadeState.empty(), EchoShadeState.read(null, OVERWORLD));
        final CompoundTag foreign = new CompoundTag();
        foreign.putInt("Version", 99);
        assertEquals(EchoShadeState.empty(), EchoShadeState.read(foreign, OVERWORLD));
    }

    @Test
    void aMalformedStoredMarkIdIsDiscardedInsteadOfThrowing() {
        final CompoundTag tag = EchoShadeState.empty()
            .withMark(Mark.of(MARK, OVERWORLD)).write();
        tag.putString("MarkId", "not-a-uuid");
        assertFalse(EchoShadeState.read(tag, OVERWORLD).mark().present());
    }

    @Test
    void aRepresentativePopulatedStateEncodesBelowTheDeclaredCeiling() {
        final EchoShadeState populated = new EchoShadeState(
            EchoShadeState.SCHEMA_VERSION, Phase.ANSWER, Mark.of(MARK, OVERWORLD),
            new Echo(EchoShadeRules.EPISODE_TICKS, EchoShadeRules.RECORD_TICKS,
                EchoShadeRules.SAMPLE_INTERVAL_TICKS, EchoShadeRules.ANSWER_TICKS,
                EchoShadeRules.STRIKE_TICKS, EchoShadeRules.RECOVER_TICKS,
                EchoShadeRules.MAX_RECORDED_MILLIS, -EchoShadeRules.MAX_RECORDED_MILLIS,
                EchoShadeRules.MAX_SAMPLES, EchoShadeRules.MAX_STRIKES),
            new RouteLedger(ApparitionEpisodeRules.PATH_INTERVAL_TICKS,
                ApparitionEpisodeRules.MAX_ROUTE_FAILURES,
                ApparitionEpisodeRules.ROUTE_BACKOFF_TICKS),
            EchoShadeRules.COOLDOWN_TICKS
        );
        assertTrue(encodedBytes(populated.write()) < ApparitionEpisodeRules.MAX_STATE_BYTES,
            "the persisted echo stays far below the declared per-entity state ceiling");
    }

    private static int encodedBytes(final CompoundTag tag) {
        try (ByteArrayOutputStream bytes = new ByteArrayOutputStream();
             DataOutputStream out = new DataOutputStream(bytes)) {
            NbtIo.write(tag, out);
            return bytes.size();
        } catch (final IOException failure) {
            throw new UncheckedIOException(failure);
        }
    }
}
