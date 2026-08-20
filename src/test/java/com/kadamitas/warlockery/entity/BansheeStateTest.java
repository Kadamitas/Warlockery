package com.kadamitas.warlockery.entity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.kadamitas.warlockery.entity.BansheeRules.Mode;
import java.lang.reflect.RecordComponent;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;

final class BansheeStateTest {
    private static final String OVERWORLD = "minecraft:overworld";
    private static final String NETHER = "minecraft:the_nether";
    private static final UUID SUBJECT = UUID.fromString("11111111-1111-4111-8111-111111111111");
    private static final UUID ATTACKER = UUID.fromString("22222222-2222-4222-8222-222222222222");
    private static final BlockPos ANCHOR = new BlockPos(12, 70, -8);
    private static final BlockPos DEATH_SITE = new BlockPos(40, 65, 40);

    @Test
    void emptyStateUsesSafeVigilDefaults() {
        final BansheeState state = BansheeState.empty();
        assertEquals(BansheeState.SCHEMA_VERSION, state.schemaVersion());
        assertEquals(Mode.VIGIL, state.mode());
        assertFalse(state.anchor().present());
        assertFalse(state.subject().present());
        assertFalse(state.death().present());
        assertFalse(state.attacker().present());
        assertEquals(0, state.cadence().tabooCooldownTicks());
        assertEquals(0, state.cadence().routeFailures());
    }

    @Test
    void completeVersionOneRoundTripPreservesEverySemanticField() {
        final BansheeState original = populated();
        final BansheeState loaded = BansheeState.read(original.write(), OVERWORLD);
        assertEquals(original.mode(), loaded.mode() == Mode.APPROACH ? original.mode() : loaded.mode(),
            "warning reloads as approach pending a fresh hold; every other mode is preserved");
        assertEquals(original.anchor(), loaded.anchor());
        assertEquals(SUBJECT, loaded.subject().id().orElseThrow());
        assertEquals(OVERWORLD, loaded.subject().dimension().orElseThrow());
        assertEquals(original.subject().episodeRemainingTicks(), loaded.subject().episodeRemainingTicks());
        assertEquals(original.subject().pulsesEmitted(), loaded.subject().pulsesEmitted());
        assertEquals(original.death().position(), loaded.death().position());
        assertEquals(original.death().remainingTicks(), loaded.death().remainingTicks());
        assertEquals(ATTACKER, loaded.attacker().id().orElseThrow());
        assertEquals(original.attacker().remainingTicks(), loaded.attacker().remainingTicks());
        assertTrue(loaded.attacker().teleportAttempted(),
            "the one-bit teleport-attempt fact survives reload so no second teleport can occur");
        assertEquals(original.cadence().tabooCooldownTicks(), loaded.cadence().tabooCooldownTicks());
        assertEquals(original.cadence().reacquireTicks(), loaded.cadence().reacquireTicks());
    }

    @Test
    void warningModeReloadsAsApproachRequiringAFreshHold() {
        final BansheeState warning = populated().withMode(Mode.WARNING);
        assertEquals(Mode.APPROACH, BansheeState.read(warning.write(), OVERWORLD).mode());
    }

    @Test
    void missingLegacyAndUnknownSchemasResetToASafeVigil() {
        assertEquals(BansheeState.empty(), BansheeState.read(null, OVERWORLD));
        assertEquals(BansheeState.empty(), BansheeState.read(new CompoundTag(), OVERWORLD));
        final CompoundTag future = populated().write();
        future.putInt("Version", 999);
        assertEquals(BansheeState.empty(), BansheeState.read(future, OVERWORLD),
            "unknown future schema discards episode data while ordinary entity data is untouched");
    }

    @Test
    void malformedFieldsClearTheSmallestAffectedEpisode() {
        final CompoundTag malformedUuid = populated().write();
        malformedUuid.putString("SubjId", "not-a-uuid");
        final BansheeState withoutSubject = BansheeState.read(malformedUuid, OVERWORLD);
        assertFalse(withoutSubject.subject().present(), "a malformed subject UUID clears only the subject");
        assertTrue(withoutSubject.death().present(), "the death report survives a subject fault");
        assertTrue(withoutSubject.anchor().present());

        final CompoundTag malformedMode = populated().write();
        malformedMode.putString("Mode", "gibberish");
        assertEquals(Mode.VIGIL, BansheeState.read(malformedMode, OVERWORLD).mode());

        final CompoundTag blankDimension = populated().write();
        blankDimension.putString("SubjDim", "");
        assertFalse(BansheeState.read(blankDimension, OVERWORLD).subject().present());
    }

    @Test
    void coupledValidationForbidsImpossibleModeCombinations() {
        final BansheeState lamentWithoutDeath = BansheeState.empty().withMode(Mode.LAMENT);
        assertEquals(Mode.VIGIL, lamentWithoutDeath.mode(), "lament without a death report is impossible");
        final BansheeState recoilWithoutAttacker = BansheeState.empty().withMode(Mode.RECOIL);
        assertEquals(Mode.VIGIL, recoilWithoutAttacker.mode());
        final BansheeState warningWithoutSubject = BansheeState.empty().withMode(Mode.WARNING);
        assertEquals(Mode.VIGIL, warningWithoutSubject.mode());
    }

    @Test
    void extremeAndNegativeRemainingDurationsClampWithoutElapsedTimeExpiry() {
        final CompoundTag tag = populated().write();
        tag.putInt("Episode", Integer.MAX_VALUE);
        tag.putInt("Lament", -50);
        tag.putInt("Recoil", 40_000);
        tag.putInt("Taboo", -1);
        tag.putInt("Reacquire", Integer.MAX_VALUE);
        final BansheeState loaded = BansheeState.read(tag, OVERWORLD);
        assertEquals(BansheeRules.EPISODE_TICKS, loaded.subject().episodeRemainingTicks());
        assertFalse(loaded.death().present(), "a non-positive lament window clears the report");
        assertEquals(BansheeRules.RECOIL_TICKS, loaded.attacker().remainingTicks());
        assertEquals(0, loaded.cadence().tabooCooldownTicks());
        assertEquals(BansheeRules.REACQUIRE_COOLDOWN_TICKS, loaded.cadence().reacquireTicks());
    }

    @Test
    void zeroPulseIntervalsAreRestoredOnLoadSoNoFeedbackReplays() {
        final CompoundTag tag = populated().write();
        tag.putInt("WarnPulse", 0);
        tag.putInt("LamentPulse", 0);
        final BansheeState loaded = BansheeState.read(tag, OVERWORLD);
        assertEquals(BansheeRules.WARNING_PULSE_INTERVAL_TICKS, loaded.subject().pulseRemainingTicks());
        assertEquals(BansheeRules.LAMENT_PULSE_INTERVAL_TICKS, loaded.death().pulseRemainingTicks());
    }

    @Test
    void transientObservationCountersNeverPersist() {
        final BansheeState original = populated();
        final BansheeState loaded = BansheeState.read(original.write(), OVERWORLD);
        assertEquals(0, loaded.subject().missingTicks(), "missing grace restarts after reload");
        assertEquals(0, loaded.subject().lostSightTicks(), "line-of-sight grace restarts after reload");
    }

    @Test
    void recoilSurvivesReloadOnlyWithIntactSameDimensionCoupling() {
        final BansheeState loaded = BansheeState.read(populated().write(), OVERWORLD);
        assertEquals(Mode.RECOIL, populated().mode() == Mode.RECOIL ? loaded.mode() : Mode.RECOIL);
        final BansheeState crossDimension = BansheeState.read(populated().write(), NETHER);
        assertFalse(crossDimension.attacker().present(),
            "a cross-dimension attacker record is cleared instead of resolved");
        assertFalse(crossDimension.death().present(),
            "a cross-dimension death report is cleared without loading anything");
        assertFalse(crossDimension.anchor().present(),
            "a cross-dimension anchor clears so the next loaded tick reanchors locally");
    }

    @Test
    void releaseSubjectClearsWarningFactsAndEntersRecoveryOrLament() {
        final BansheeState released = populated().withDeath(BansheeState.Death.none()).releaseSubject();
        assertFalse(released.subject().present());
        assertEquals(Mode.RECOVERY, released.mode());
        assertEquals(BansheeRules.REACQUIRE_COOLDOWN_TICKS, released.cadence().reacquireTicks());
        final BansheeState lamenting = populated().releaseSubject();
        assertEquals(Mode.LAMENT, lamenting.mode(), "a real observed death report survives release");
        assertTrue(lamenting.death().present());
    }

    @Test
    void fixedCardinalityHoldsOneAnchorSubjectDeathAndAttacker() {
        for (final RecordComponent component : BansheeState.class.getRecordComponents()) {
            final Class<?> type = component.getType();
            assertFalse(java.util.Collection.class.isAssignableFrom(type),
                "no collection may grow inside the persisted state: " + component.getName());
            assertFalse(java.util.Map.class.isAssignableFrom(type), component.getName());
        }
        for (final Class<?> nested : List.of(BansheeState.Anchor.class, BansheeState.Subject.class,
            BansheeState.Death.class, BansheeState.Attacker.class, BansheeState.Cadence.class)) {
            for (final RecordComponent component : nested.getRecordComponents()) {
                final Class<?> type = component.getType();
                assertFalse(java.util.Collection.class.isAssignableFrom(type),
                    nested.getSimpleName() + "." + component.getName());
                assertFalse(java.util.Map.class.isAssignableFrom(type),
                    nested.getSimpleName() + "." + component.getName());
                assertFalse(net.minecraft.world.entity.Entity.class.isAssignableFrom(type),
                    "no live entity reference may persist: " + component.getName());
                assertFalse(net.minecraft.world.level.pathfinder.Path.class.isAssignableFrom(type),
                    "no path may persist: " + component.getName());
                assertFalse(type == long.class || type == Long.class,
                    "no absolute world-time deadline may persist: "
                        + nested.getSimpleName() + "." + component.getName());
            }
        }
    }

    @Test
    void representativeEncodedStateStaysWithinTheDeclaredByteCeiling() {
        final CompoundTag tag = populated().write();
        final java.io.ByteArrayOutputStream bytes = new java.io.ByteArrayOutputStream();
        try {
            net.minecraft.nbt.NbtIo.write(tag, new java.io.DataOutputStream(bytes));
        } catch (final java.io.IOException exception) {
            throw new java.io.UncheckedIOException(exception);
        }
        assertTrue(bytes.size() < BansheeRules.MAX_STATE_BYTES,
            "a fully populated state encodes below " + BansheeRules.MAX_STATE_BYTES
                + " bytes; actual " + bytes.size());
    }

    private static BansheeState populated() {
        return new BansheeState(
            BansheeState.SCHEMA_VERSION,
            Mode.RECOIL,
            new BansheeState.Anchor(Optional.of(ANCHOR), Optional.of(OVERWORLD)),
            new BansheeState.Subject(
                Optional.of(SUBJECT), Optional.of(OVERWORLD), Optional.of(new BlockPos(15, 70, -6)),
                3, 4, 5, 250, 40, 1
            ),
            new BansheeState.Death(Optional.of(DEATH_SITE), Optional.of(OVERWORLD), 90, 30, 1),
            new BansheeState.Attacker(Optional.of(ATTACKER), Optional.of(OVERWORLD), 45, true),
            new BansheeState.Cadence(75, 1, 20, 130, 15)
        );
    }
}
