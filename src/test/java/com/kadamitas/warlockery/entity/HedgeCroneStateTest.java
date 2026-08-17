package com.kadamitas.warlockery.entity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.kadamitas.warlockery.entity.HedgeCroneRules.Action;
import com.kadamitas.warlockery.entity.HedgeCroneRules.Hex;
import com.kadamitas.warlockery.entity.HedgeCroneRules.Mode;
import com.kadamitas.warlockery.entity.HedgeCroneRules.ThreatClass;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;

/** Defaults, round trip, malformed schema, coupled validation, and cancellation contracts. */
final class HedgeCroneStateTest {
    private static final String HERE = "minecraft:overworld";
    private static final String ELSEWHERE = "minecraft:the_nether";

    @Test
    void defaultsAreSafeAndCompletelyIdle() {
        final HedgeCroneState state = HedgeCroneState.empty();
        assertEquals(HedgeCroneState.SCHEMA_VERSION, state.schemaVersion());
        assertEquals(Mode.IDLE, state.mode());
        assertFalse(state.anchor().present());
        assertFalse(state.threat().present());
        assertFalse(state.action().pending());
        assertFalse(state.work().wardPrepared());
        assertFalse(state.work().hasWorkstation());
        assertEquals(0, state.cadence().routeFailures());
    }

    @Test
    void aCompleteRoundTripPreservesEveryIndependentlyValidFact() {
        final HedgeCroneState state = HedgeCroneState.empty()
            .withAnchor(new HedgeCroneState.Anchor(Optional.of(new BlockPos(4, 65, -9)), Optional.of(HERE)))
            .withWork(new HedgeCroneState.Work(true, Optional.of(new BlockPos(1, 64, 1)),
                Optional.of(HERE), 900, 40))
            .withCadence(new HedgeCroneState.Cadence(45, 0, 2, 60, 300));

        final HedgeCroneState loaded = HedgeCroneState.read(state.write(), HERE);
        assertEquals(new BlockPos(4, 65, -9), loaded.anchor().position().orElseThrow());
        assertEquals(HERE, loaded.anchor().dimension().orElseThrow());
        assertTrue(loaded.work().wardPrepared(), "the ward boolean is an independently valid fact");
        assertEquals(900, loaded.work().wardCooldownTicks());
        assertEquals(40, loaded.work().workstationSearchTicks());
        assertEquals(45, loaded.cadence().castRecoveryTicks());
        assertEquals(300, loaded.cadence().anchorUnavailableTicks());
        assertFalse(loaded.work().hasWorkstation(), "a destination is transient and never resumed");
        assertEquals(0, loaded.cadence().routeFailures(), "route failures never survive an unload");
    }

    @Test
    void encodedStateStaysWellUnderTheDeclaredCeiling() {
        final CompoundTag tag = HedgeCroneState.empty()
            .withAnchor(new HedgeCroneState.Anchor(Optional.of(new BlockPos(9, 70, 9)), Optional.of(HERE)))
            .withThreat(HedgeCroneState.Threat.escalated(UUID.randomUUID(), HERE))
            .withAction(HedgeCroneState.ActionState.hex(UUID.randomUUID(), HERE, Hex.BINDING))
            .withWork(new HedgeCroneState.Work(true, Optional.of(new BlockPos(2, 64, 2)),
                Optional.of(HERE), 1_000, 80))
            .withCadence(new HedgeCroneState.Cadence(60, 100, 3, 100, 1_000))
            .write();
        assertTrue(tag.size() > 0);
        assertTrue(encodedBytes(tag) < HedgeCroneRules.MAX_STATE_BYTES,
            "a representative populated Crone state must stay small");
    }

    @Test
    void unknownOrMalformedSchemaFallsBackToSafeDefaults() {
        assertEquals(HedgeCroneState.empty(), HedgeCroneState.read(null, HERE));
        final CompoundTag future = HedgeCroneState.empty().write();
        future.putInt("Version", HedgeCroneState.SCHEMA_VERSION + 41);
        assertEquals(HedgeCroneState.empty(), HedgeCroneState.read(future, HERE));
        final CompoundTag missing = new CompoundTag();
        assertEquals(HedgeCroneState.empty(), HedgeCroneState.read(missing, HERE));
    }

    @Test
    void aDimensionMismatchDiscardsTheAnchorWithoutErasingTheWard() {
        final CompoundTag tag = HedgeCroneState.empty()
            .withAnchor(new HedgeCroneState.Anchor(Optional.of(BlockPos.ZERO), Optional.of(ELSEWHERE)))
            .withWork(new HedgeCroneState.Work(true, Optional.empty(), Optional.empty(), 10, 0))
            .write();
        final HedgeCroneState loaded = HedgeCroneState.read(tag, HERE);
        assertFalse(loaded.anchor().present());
        assertTrue(loaded.work().wardPrepared());
    }

    @Test
    void everyLiveActionWarningAndTargetIsCanceledByLoading() {
        final CompoundTag tag = HedgeCroneState.empty()
            .withThreat(HedgeCroneState.Threat.warned(UUID.randomUUID(), HERE))
            .withAction(HedgeCroneState.ActionState.hex(UUID.randomUUID(), HERE, Hex.VEIL))
            .withMode(Mode.CASTING)
            .write();
        final HedgeCroneState loaded = HedgeCroneState.read(tag, HERE);
        assertEquals(Mode.IDLE, loaded.mode());
        assertFalse(loaded.threat().present(), "a warning can never rebind to a replacement entity");
        assertFalse(loaded.action().pending(), "an attack is never replayed after a reload");
    }

    @Test
    void anActionTargetWithoutItsCoupledFieldsCancelsImmediately() {
        final HedgeCroneState.ActionState orphaned = new HedgeCroneState.ActionState(
            Action.HEX, Optional.of(UUID.randomUUID()), Optional.empty(), Optional.of(Hex.WITHER), 20
        );
        assertEquals(Action.NONE, orphaned.action());
        assertFalse(orphaned.pending());

        final HedgeCroneState.ActionState hexWithoutHex = new HedgeCroneState.ActionState(
            Action.HEX, Optional.of(UUID.randomUUID()), Optional.of(HERE), Optional.empty(), 20
        );
        assertEquals(Action.NONE, hexWithoutHex.action());

        final HedgeCroneState.ActionState illegalWorkTarget = new HedgeCroneState.ActionState(
            Action.WARD_PREPARATION, Optional.of(UUID.randomUUID()), Optional.of(HERE), Optional.empty(), 60
        );
        assertEquals(Action.NONE, illegalWorkTarget.action(),
            "a ward preparation never carries a target identity");
    }

    @Test
    void aThreatWithoutItsCoupledIdentityCollapsesButAnExpiredOneSurvives() {
        assertFalse(new HedgeCroneState.Threat(
            Optional.of(UUID.randomUUID()), Optional.empty(), ThreatClass.DIRECT, 200, 0, 0).present(),
            "structural coupling is still validated");
        assertEquals(ThreatClass.NONE, new HedgeCroneState.Threat(
            Optional.empty(), Optional.of(HERE), ThreatClass.BOUNDARY_ESCALATED, 200, 0, 0).threatClass());

        // Phase ending belongs to tick dispatch alone: an expired threat stays observable so
        // HedgeCroneRules.threatReleases can end it once and the release can be counted.
        final HedgeCroneState.Threat expired = new HedgeCroneState.Threat(
            Optional.of(UUID.randomUUID()), Optional.of(HERE), ThreatClass.DIRECT, 0, 0, 0);
        assertTrue(expired.present(), "the expiry transition stays observable");
        assertTrue(HedgeCroneRules.threatReleases(
            expired.threatClass(), true, 1.0D, 0, expired.remainingTicks()),
            "and the rule that ends it fires on exactly that fact");
    }

    @Test
    void everyStoredDurationIsClampedIntoItsDeclaredBound() {
        final HedgeCroneState.Threat threat = new HedgeCroneState.Threat(
            Optional.of(UUID.randomUUID()), Optional.of(HERE), ThreatClass.DIRECT,
            Integer.MAX_VALUE, Integer.MAX_VALUE, Integer.MAX_VALUE
        );
        assertEquals(HedgeCroneRules.THREAT_TICKS, threat.remainingTicks());
        assertEquals(HedgeCroneRules.WARNING_TICKS, threat.warningRemainingTicks());
        assertEquals(HedgeCroneRules.LOST_SIGHT_RELEASE_TICKS, threat.ticksWithoutSight());

        final HedgeCroneState.Cadence cadence =
            new HedgeCroneState.Cadence(-5, Integer.MAX_VALUE, 99, Integer.MIN_VALUE, Integer.MAX_VALUE);
        assertEquals(0, cadence.castRecoveryTicks());
        assertEquals(HedgeCroneRules.WITHDRAW_TICKS, cadence.withdrawalTicks());
        assertEquals(HedgeCroneRules.MAX_ROUTE_FAILURES, cadence.routeFailures());
        assertEquals(0, cadence.routeRetryTicks());
        assertEquals(HedgeCroneRules.ANCHOR_REPLACE_TICKS, cadence.anchorUnavailableTicks());

        final HedgeCroneState.Work work = new HedgeCroneState.Work(
            true, Optional.of(BlockPos.ZERO), Optional.of(HERE), Integer.MAX_VALUE, Integer.MAX_VALUE
        );
        assertEquals(HedgeCroneRules.WARD_COOLDOWN_TICKS, work.wardCooldownTicks());
        assertEquals(HedgeCroneRules.WORKSTATION_INTERVAL_TICKS, work.workstationSearchTicks());
    }

    @Test
    void theModeIsStoredExactlyAsSetBecauseTheRuntimeOwnsIt() {
        for (final Mode mode : Mode.values()) {
            assertEquals(mode, HedgeCroneState.empty().withMode(mode).mode());
        }
        assertEquals(Mode.IDLE, HedgeCroneState.read(
            HedgeCroneState.empty().withMode(Mode.CASTING).write(), HERE).mode(),
            "a reloaded Crone still always resumes idle");
    }

    @Test
    void cancellationDropsTheActionAndDestinationButKeepsTheWard() {
        final HedgeCroneState casting = HedgeCroneState.empty()
            .withWork(new HedgeCroneState.Work(true, Optional.of(BlockPos.ZERO), Optional.of(HERE), 500, 10))
            .withAction(HedgeCroneState.ActionState.hex(UUID.randomUUID(), HERE, Hex.ENFEEBLE))
            .withMode(Mode.CASTING);
        final HedgeCroneState canceled = casting.cancelAction();
        assertFalse(canceled.action().pending());
        assertFalse(canceled.work().hasWorkstation());
        assertTrue(canceled.work().wardPrepared());
        assertEquals(500, canceled.work().wardCooldownTicks());
        assertEquals(Mode.IDLE, canceled.mode());
    }

    @Test
    void theStateExposesNoCollectionPathOrLiveReferenceComponent() {
        final List<Class<?>> componentTypes = java.util.Arrays.stream(
                HedgeCroneState.class.getRecordComponents())
            .map(java.lang.reflect.RecordComponent::getType)
            .toList();
        assertTrue(componentTypes.stream().noneMatch(type ->
            java.util.Collection.class.isAssignableFrom(type)
                || java.util.Map.class.isAssignableFrom(type)
                || net.minecraft.world.entity.Entity.class.isAssignableFrom(type)
                || net.minecraft.world.level.Level.class.isAssignableFrom(type)
                || net.minecraft.world.level.pathfinder.Path.class.isAssignableFrom(type)));
        assertTrue(nestedComponentTypes().stream().noneMatch(type ->
            java.util.Collection.class.isAssignableFrom(type)
                || java.util.Map.class.isAssignableFrom(type)
                || net.minecraft.world.entity.Entity.class.isAssignableFrom(type)
                || net.minecraft.world.level.pathfinder.Path.class.isAssignableFrom(type)));
    }

    private static List<Class<?>> nestedComponentTypes() {
        return java.util.stream.Stream.of(
                HedgeCroneState.Anchor.class,
                HedgeCroneState.Threat.class,
                HedgeCroneState.ActionState.class,
                HedgeCroneState.Work.class,
                HedgeCroneState.Cadence.class)
            .flatMap(record -> java.util.Arrays.stream(record.getRecordComponents()))
            .map(java.lang.reflect.RecordComponent::getType)
            .toList();
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
