package com.kadamitas.warlockery.entity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.kadamitas.warlockery.entity.ApparitionEpisodeRules.RouteLedger;
import com.kadamitas.warlockery.entity.SpectreRules.Phase;
import com.kadamitas.warlockery.entity.SpectreState.Haunt;
import com.kadamitas.warlockery.entity.SpectreState.Witness;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtIo;
import org.junit.jupiter.api.Test;

/** Persistence, clamping and reconciliation contracts for the F21 Spectre state record. */
final class SpectreStateTest {
    private static final String OVERWORLD = "minecraft:overworld";
    private static final String NETHER = "minecraft:the_nether";
    private static final UUID WITNESS = UUID.fromString("00000000-0000-0000-0000-0000000000bb");

    @Test
    void anEmptyStateIsAQuietDriftWithNothingClaimed() {
        final SpectreState empty = SpectreState.empty();
        assertEquals(SpectreState.SCHEMA_VERSION, empty.schemaVersion());
        assertEquals(Phase.DRIFT, empty.phase());
        assertFalse(empty.witness().present());
        assertFalse(empty.haunt().active());
        assertEquals(0, empty.cooldownTicks());
    }

    // ------------------------------------------------------------ reconciliation shape

    /**
     * The identity shape of reconciliation: the two halves of one witness cannot disagree. This is
     * the type asserting a coupled invariant, not a constructor deciding that something ended.
     */
    @Test
    void aHalfWrittenWitnessCollapsesToNoWitnessAtAll() {
        assertFalse(new Witness(Optional.of(WITNESS), Optional.empty()).present());
        assertFalse(new Witness(Optional.empty(), Optional.of(OVERWORLD)).present());
        assertFalse(new Witness(Optional.of(WITNESS), Optional.of(" ")).present());
        assertTrue(Witness.of(WITNESS, OVERWORLD).present());
    }

    /**
     * The timer shape of reconciliation is the defect, and it is absent here on purpose. A haunting
     * whose total budget has run out must keep its sibling timers intact so the tick branch that
     * owns ending the current phase can still observe its own expiry and arm the fade it owes.
     */
    @Test
    void anExpiredHauntKeepsItsSiblingTimersSoNoTickBranchLosesItsEnding() {
        final Haunt expired = new Haunt(0, 0, 0, 30, 0, 2, 0);
        assertFalse(expired.active(), "the total budget is genuinely spent");
        assertEquals(30, expired.dreadRemainingTicks(),
            "the dread window survives so its owning branch still runs and arms the fade");
        assertEquals(2, expired.telegraphs(),
            "the telegraph count is not silently discarded by the constructor");
    }

    @Test
    void aDeliveredDreadIsNeverReconciledAwayByAnySiblingField() {
        final Haunt spent = new Haunt(0, 0, 0, 0, 0, 0, SpectreRules.MAX_DREADS);
        assertEquals(SpectreRules.MAX_DREADS, spent.dreads(),
            "no reload or sibling timer may ever hand back the one spent delivery");
    }

    // ------------------------------------------------------------ clamping

    @Test
    void everyDurationAndCounterIsClampedIntoItsDeclaredRange() {
        final Haunt wild = new Haunt(9_999, 9_999, 9_999, 9_999, 9_999, 999, 999);
        assertEquals(SpectreRules.EPISODE_TICKS, wild.remainingTicks());
        assertEquals(SpectreRules.MANIFEST_TICKS, wild.manifestRemainingTicks());
        assertEquals(SpectreRules.TELEGRAPH_INTERVAL_TICKS, wild.telegraphRemainingTicks());
        assertEquals(SpectreRules.DREAD_TICKS, wild.dreadRemainingTicks());
        assertEquals(SpectreRules.FADE_TICKS, wild.fadeRemainingTicks());
        assertEquals(SpectreRules.MAX_TELEGRAPHS, wild.telegraphs());
        assertEquals(SpectreRules.MAX_DREADS, wild.dreads());

        final Haunt negative = new Haunt(-1, -1, -1, -1, -1, -1, -1);
        assertEquals(0, negative.remainingTicks());
        assertEquals(0, negative.telegraphs());
        assertEquals(0, negative.dreads());

        assertEquals(SpectreRules.COOLDOWN_TICKS,
            SpectreState.empty().withCooldown(9_999).cooldownTicks());
    }

    @Test
    void aFreshHauntStartsWithItsTelegraphArmedAndNothingDelivered() {
        final Haunt started = Haunt.started();
        assertTrue(started.active());
        assertEquals(SpectreRules.MANIFEST_TICKS, started.manifestRemainingTicks());
        assertEquals(SpectreRules.TELEGRAPH_INTERVAL_TICKS, started.telegraphRemainingTicks());
        assertEquals(0, started.telegraphs());
        assertEquals(0, started.dreads());
        assertEquals(0, started.dreadRemainingTicks(),
            "no dread window is open before the manifestation has graduated");
    }

    // ------------------------------------------------------------ endings

    @Test
    void endingAHauntReleasesTheWitnessAndArmsTheCadenceInOneWrite() {
        final SpectreState running = SpectreState.empty()
            .withWitness(Witness.of(WITNESS, OVERWORLD))
            .withHaunt(Haunt.started())
            .withRoute(new RouteLedger(0, 2, 0))
            .withPhase(Phase.MANIFEST);
        final SpectreState ended = running.endHaunt();
        assertEquals(Phase.DRIFT, ended.phase());
        assertFalse(ended.witness().present(), "no witness survives the ending");
        assertFalse(ended.haunt().active(), "no haunting survives the ending");
        assertEquals(0, ended.route().routeFailures());
        assertEquals(SpectreRules.COOLDOWN_TICKS, ended.cooldownTicks(),
            "the ending arms the cadence so a second haunting cannot start immediately");
    }

    // ------------------------------------------------------------ persistence

    @Test
    void aPopulatedStateRoundTripsThroughItsOwnEncoding() {
        final SpectreState original = new SpectreState(
            SpectreState.SCHEMA_VERSION, Phase.MANIFEST, Witness.of(WITNESS, OVERWORLD),
            new Haunt(420, 60, 12, 0, 0, 2, 0),
            new RouteLedger(9, 1, 30), 0
        );
        assertEquals(original, SpectreState.read(original.write(), OVERWORLD));
    }

    @Test
    void aReloadNeverResumesInsideAnOpenDreadWindowButKeepsTheDeliveredDread() {
        final SpectreState dreading = new SpectreState(
            SpectreState.SCHEMA_VERSION, Phase.DREAD, Witness.of(WITNESS, OVERWORLD),
            new Haunt(300, 0, 0, 40, 0, 4, 1),
            new RouteLedger(0, 0, 0), 0
        );
        final SpectreState restored = SpectreState.read(dreading.write(), OVERWORLD);
        assertEquals(Phase.MANIFEST, restored.phase(),
            "a reload demotes an open dread window so the Spectre must telegraph again");
        assertEquals(1, restored.haunt().dreads(),
            "the delivered dread survives the reload so no second delivery is granted");
    }

    @Test
    void aReloadRestoresTheTelegraphIntervalSoNoFeedbackReplays() {
        final SpectreState saved = new SpectreState(
            SpectreState.SCHEMA_VERSION, Phase.MANIFEST, Witness.of(WITNESS, OVERWORLD),
            new Haunt(300, 50, 0, 0, 0, 1, 0),
            new RouteLedger(0, 0, 0), 0
        );
        assertEquals(SpectreRules.TELEGRAPH_INTERVAL_TICKS,
            SpectreState.read(saved.write(), OVERWORLD).haunt().telegraphRemainingTicks(),
            "a persisted zero interval is restored, so a load never fires a telegraph immediately");
    }

    @Test
    void aWitnessFromAnotherDimensionIsDroppedRatherThanPursued() {
        final SpectreState saved = SpectreState.empty()
            .withWitness(Witness.of(WITNESS, OVERWORLD))
            .withHaunt(Haunt.started())
            .withPhase(Phase.MANIFEST);
        assertFalse(SpectreState.read(saved.write(), NETHER).witness().present());
    }

    @Test
    void anUnknownOrMissingSchemaResetsToASafeDrift() {
        assertEquals(SpectreState.empty(), SpectreState.read(null, OVERWORLD));
        final CompoundTag foreign = new CompoundTag();
        foreign.putInt("Version", 99);
        assertEquals(SpectreState.empty(), SpectreState.read(foreign, OVERWORLD));
    }

    @Test
    void aMalformedStoredWitnessIdIsDiscardedInsteadOfThrowing() {
        final CompoundTag tag = SpectreState.empty()
            .withWitness(Witness.of(WITNESS, OVERWORLD)).write();
        tag.putString("WitnessId", "not-a-uuid");
        assertFalse(SpectreState.read(tag, OVERWORLD).witness().present());
    }

    @Test
    void aRepresentativePopulatedStateEncodesBelowTheDeclaredCeiling() {
        final SpectreState populated = new SpectreState(
            SpectreState.SCHEMA_VERSION, Phase.MANIFEST, Witness.of(WITNESS, OVERWORLD),
            new Haunt(SpectreRules.EPISODE_TICKS, SpectreRules.MANIFEST_TICKS,
                SpectreRules.TELEGRAPH_INTERVAL_TICKS, SpectreRules.DREAD_TICKS,
                SpectreRules.FADE_TICKS, SpectreRules.MAX_TELEGRAPHS, SpectreRules.MAX_DREADS),
            new RouteLedger(ApparitionEpisodeRules.PATH_INTERVAL_TICKS,
                ApparitionEpisodeRules.MAX_ROUTE_FAILURES,
                ApparitionEpisodeRules.ROUTE_BACKOFF_TICKS),
            SpectreRules.COOLDOWN_TICKS
        );
        assertTrue(encodedBytes(populated.write()) < ApparitionEpisodeRules.MAX_STATE_BYTES,
            "the persisted haunting stays far below the declared per-entity state ceiling");
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
