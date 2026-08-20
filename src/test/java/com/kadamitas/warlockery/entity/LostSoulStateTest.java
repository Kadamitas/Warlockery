package com.kadamitas.warlockery.entity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.kadamitas.warlockery.entity.LostSoulRules.Phase;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtIo;
import org.junit.jupiter.api.Test;

/** Persistence, clamping and reconciliation contracts for the Lost Soul semantic state. */
final class LostSoulStateTest {
    private static final String HERE = "minecraft:overworld";
    private static final String ELSEWHERE = "minecraft:the_nether";

    private static LostSoulState populated() {
        return LostSoulState.empty()
            .withAnchor(LostSoulState.Anchor.at(new BlockPos(12, 64, -30), HERE))
            .withEpisode(new LostSoulState.Episode(300, 40, 0, 15, 2))
            .withCadence(new LostSoulState.Cadence(0, 2, 30))
            .withPhase(Phase.PETITION);
    }

    @Test
    void anEmptyStateIsAVersionedWanderWithNothingHeld() {
        final LostSoulState empty = LostSoulState.empty();
        assertEquals(LostSoulState.SCHEMA_VERSION, empty.schemaVersion());
        assertEquals(Phase.WANDER, empty.phase());
        assertFalse(empty.anchor().present());
        assertFalse(empty.episode().active());
        assertEquals(0, empty.cadence().routeFailures());
        assertEquals(0, empty.cadence().cooldownTicks());
    }

    @Test
    void everyStoredDurationAndCountIsClamped() {
        final LostSoulState.Episode episode =
            new LostSoulState.Episode(Integer.MAX_VALUE, -40, 9_999, -1, 99);
        assertEquals(LostSoulRules.EPISODE_TICKS, episode.remainingTicks());
        assertEquals(0, episode.petitionRemainingTicks());
        assertEquals(LostSoulRules.SETTLE_TICKS, episode.settleRemainingTicks());
        assertEquals(0, episode.pulseRemainingTicks());
        assertEquals(LostSoulRules.MAX_PETITION_PULSES, episode.pulsesEmitted());

        final LostSoulState.Cadence cadence =
            new LostSoulState.Cadence(-9, 42, Integer.MAX_VALUE);
        assertEquals(0, cadence.cooldownTicks());
        assertEquals(LostSoulRules.MAX_ROUTE_FAILURES, cadence.routeFailures());
        assertEquals(LostSoulRules.ROUTE_BACKOFF_TICKS, cadence.routeRetryTicks());
    }

    @Test
    void anExpiredEpisodeDropsEveryDependentPetitionFact() {
        final LostSoulState.Episode expired = new LostSoulState.Episode(0, 50, 50, 20, 3);
        assertEquals(0, expired.petitionRemainingTicks());
        assertEquals(0, expired.settleRemainingTicks());
        assertEquals(0, expired.pulseRemainingTicks());
        assertEquals(0, expired.pulsesEmitted());
        assertFalse(expired.active());
    }

    @Test
    void aHalfPopulatedAnchorCollapsesToNoAnchorAtAll() {
        assertFalse(new LostSoulState.Anchor(Optional.of(BlockPos.ZERO), Optional.empty()).present());
        assertFalse(new LostSoulState.Anchor(Optional.empty(), Optional.of(HERE)).present());
        assertFalse(new LostSoulState.Anchor(Optional.of(BlockPos.ZERO), Optional.of("  ")).present());
        assertTrue(LostSoulState.Anchor.at(BlockPos.ZERO, HERE).present());
    }

    @Test
    void theCanonicalConstructorNeverRewritesAPhaseATickBranchIsResponsibleForEnding() {
        // Regression: reconciling an expired episode away in the constructor meant
        // endEpisodeIfRequired never observed EpisodeEnd.EXPIRED, so the anchor was never
        // released, the cooldown was never armed, and the shade could never start a new episode.
        for (final Phase phase : Phase.values()) {
            assertEquals(phase, LostSoulState.empty().withPhase(phase).phase(),
                phase + " must survive into the tick that owns ending it");
        }
        final LostSoulState expired = populated()
            .withEpisode(new LostSoulState.Episode(0, 0, 0, 0, 0));
        assertEquals(Phase.PETITION, expired.phase(),
            "an expired episode stays in its attention phase so the tick can observe the expiry");
        assertEquals(LostSoulRules.EpisodeEnd.EXPIRED, LostSoulRules.episodeEnd(
            new LostSoulRules.AnchorObservation(true, true, true, true, 0, 0, false)),
            "and the expiry is then genuinely reachable");
        assertEquals(Phase.PETITION, populated().phase());
    }

    @Test
    void bindingAtomicallyCancelsTheEpisodeTheAnchorAndTheRouteAccounting() {
        final LostSoulState bound = populated().bind();
        assertEquals(Phase.BOUND, bound.phase());
        assertFalse(bound.anchor().present(), "no memorial anchor survives a binding");
        assertFalse(bound.episode().active(), "no petition survives a binding");
        assertEquals(0, bound.episode().pulsesEmitted());
        assertEquals(0, bound.cadence().routeFailures());
        assertEquals(0, bound.cadence().routeRetryTicks());
        assertEquals(0, bound.cadence().cooldownTicks());
    }

    @Test
    void endingAnEpisodeStartsTheCooldownWithoutTouchingOwnership() {
        final LostSoulState ended = populated().endEpisode();
        assertEquals(Phase.COOLDOWN, ended.phase());
        assertFalse(ended.anchor().present());
        assertFalse(ended.episode().active());
        assertEquals(LostSoulRules.COOLDOWN_TICKS, ended.cadence().cooldownTicks());
        assertEquals(0, ended.cadence().routeFailures());

        final LostSoulState unbound = populated().bind().unbind();
        assertEquals(Phase.WANDER, unbound.phase());
        assertEquals(LostSoulRules.COOLDOWN_TICKS, unbound.cadence().cooldownTicks(),
            "losing an owner does not immediately restart a memorial episode");
    }

    @Test
    void aRoundTripPreservesEverySemanticFact() {
        final LostSoulState original = populated();
        final LostSoulState restored = LostSoulState.read(original.write(), HERE);
        assertEquals(original.anchor(), restored.anchor());
        assertEquals(original.episode().remainingTicks(), restored.episode().remainingTicks());
        assertEquals(original.episode().petitionRemainingTicks(),
            restored.episode().petitionRemainingTicks());
        assertEquals(original.episode().pulsesEmitted(), restored.episode().pulsesEmitted());
        assertEquals(original.cadence().routeFailures(), restored.cadence().routeFailures());
        assertEquals(Phase.APPROACH, restored.phase(),
            "a reload never resumes mid petition: it re-approaches its own anchor first");
    }

    @Test
    void theEpisodePulseCountSurvivesAReloadSoTheCapIsNotRefreshed() {
        final LostSoulState spent = populated()
            .withEpisode(new LostSoulState.Episode(300, 40, 0, 20,
                LostSoulRules.MAX_PETITION_PULSES));
        final LostSoulState restored = LostSoulState.read(spent.write(), HERE);
        assertEquals(LostSoulRules.MAX_PETITION_PULSES, restored.episode().pulsesEmitted(),
            "a spent petition cap is a property of the episode, not of the current approach");
        assertEquals(0, LostSoulRules.petitionPulsesRemaining(restored.episode().pulsesEmitted()));
    }

    @Test
    void aReloadNeverReplaysAPetitionPulse() {
        final LostSoulState due = populated()
            .withEpisode(new LostSoulState.Episode(300, 40, 0, 0, 1));
        final LostSoulState restored = LostSoulState.read(due.write(), HERE);
        assertEquals(LostSoulRules.PETITION_PULSE_INTERVAL_TICKS,
            restored.episode().pulseRemainingTicks(),
            "a persisted zero interval reloads as a full interval, so no feedback replays");
        assertFalse(LostSoulRules.pulseDue(restored.episode().pulseRemainingTicks(),
            restored.episode().pulsesEmitted(), LostSoulRules.MAX_PETITION_PULSES));
    }

    @Test
    void anAnchorFromAnotherDimensionIsDroppedRatherThanChased() {
        final LostSoulState restored = LostSoulState.read(populated().write(), ELSEWHERE);
        assertFalse(restored.anchor().present(),
            "an anchor never survives into another dimension");
        assertEquals(LostSoulRules.EpisodeEnd.ANCHOR_LOST, LostSoulRules.episodeEnd(
            new LostSoulRules.AnchorObservation(false, true, false, false,
                restored.episode().remainingTicks(), 0, false)),
            "the anchorless episode ends on the next tick instead of being chased");
    }

    @Test
    void malformedAndUnknownSchemasResetToASafeWander() {
        assertEquals(LostSoulState.empty(), LostSoulState.read(null, HERE));
        assertEquals(LostSoulState.empty(), LostSoulState.read(new CompoundTag(), HERE));
        final CompoundTag future = populated().write();
        future.putInt("Version", LostSoulState.SCHEMA_VERSION + 7);
        assertEquals(LostSoulState.empty(), LostSoulState.read(future, HERE));
        final CompoundTag garbled = populated().write();
        garbled.putString("Phase", "not_a_phase");
        assertEquals(Phase.WANDER, LostSoulState.read(garbled, HERE).phase());
    }

    @Test
    void aRepresentativePopulatedStateEncodesBelowTheDeclaredByteCeiling() {
        assertTrue(encode(populated().write()).length < LostSoulRules.MAX_STATE_BYTES);
        assertTrue(encode(LostSoulState.empty().write()).length < LostSoulRules.MAX_STATE_BYTES);
    }

    @Test
    void noOwnerIdentityIsEverWrittenIntoTheSpeciesState() {
        final CompoundTag encoded = populated().bind().write();
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
