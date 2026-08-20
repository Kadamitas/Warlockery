package com.kadamitas.warlockery.entity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.kadamitas.warlockery.entity.HexBatRules.Action;
import com.kadamitas.warlockery.entity.HexBatRules.DestinationPurpose;
import com.kadamitas.warlockery.entity.HexBatRules.Mode;
import com.kadamitas.warlockery.entity.HexBatRules.Provenance;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;

final class HexBatStateTest {
    private static final UUID BAT_ID = new UUID(21L, 87L);
    private static final UUID TARGET_ID = new UUID(4L, 9L);
    private static final long NOW = 48_000L;
    private static final String OVERWORLD = "minecraft:overworld";

    private static HexBatState populated() {
        return HexBatState.empty(BAT_ID, NOW)
            .withProvenance(Provenance.MURDEROUS_FLOCK)
            .withAnchor(Optional.of(new BlockPos(10, 64, -20)), Optional.of(OVERWORLD))
            .withRoost(Optional.of(new BlockPos(12, 68, -18)), Optional.of(OVERWORLD))
            .withMode(Mode.SORTIE)
            .withThreat(Optional.of(TARGET_ID), Optional.of(OVERWORLD), NOW + 60L, 0)
            .withDestination(Optional.of(new BlockPos(8, 66, -22)), DestinationPurpose.PATROL)
            .withDeadlines(new HexBatState.Deadlines(0L, 0L, NOW + 30L, NOW + 50L, 0L, NOW + 90L, NOW + 20L, NOW + 300L))
            .withRouteFailures(2);
    }

    @Test
    void defaultsAreSafeAndStaggered() {
        final HexBatState state = HexBatState.empty(BAT_ID, NOW);
        assertEquals(HexBatState.SCHEMA_VERSION, state.schemaVersion());
        assertEquals(Provenance.UNBOUND, state.provenance());
        assertEquals(Mode.SHELTER, state.mode());
        assertEquals(Action.NONE, state.action());
        assertTrue(state.anchor().isEmpty());
        assertTrue(state.roost().isEmpty());
        assertTrue(state.threatId().isEmpty());
        assertTrue(state.destination().isEmpty());
        assertEquals(DestinationPurpose.NONE, state.destinationPurpose());
        assertEquals(0, state.routeFailures());
        assertEquals(NOW + HexBatRules.stableOffset(BAT_ID, HexBatRules.TARGET_SCAN_INTERVAL_TICKS),
            state.cadence().nextTargetScanAt());
    }

    @Test
    void everyDurableSemanticFieldRoundTrips() {
        final HexBatState state = populated();
        final HexBatState loaded = HexBatState.read(state.write(), BAT_ID, NOW);
        assertEquals(Provenance.MURDEROUS_FLOCK, loaded.provenance());
        assertEquals(state.anchor(), loaded.anchor());
        assertEquals(state.anchorDimension(), loaded.anchorDimension());
        assertEquals(state.roost(), loaded.roost());
        assertEquals(state.roostDimension(), loaded.roostDimension());
        assertEquals(Mode.SORTIE, loaded.mode());
        assertEquals(state.threatId(), loaded.threatId());
        assertEquals(state.threatDimension(), loaded.threatDimension());
        assertEquals(state.threatExpiresAt(), loaded.threatExpiresAt());
        assertEquals(2, loaded.routeFailures());
        assertEquals(state.deadlines().routeBackoffUntil(), loaded.deadlines().routeBackoffUntil());
        assertEquals(state.deadlines().callDedupeUntil(), loaded.deadlines().callDedupeUntil());
        assertEquals(state.deadlines().sortieUntil(), loaded.deadlines().sortieUntil());
    }

    @Test
    void unknownSchemaVersionFallsBackToSafeDefaults() {
        final CompoundTag tag = populated().write();
        tag.putInt("Version", 99);
        final HexBatState loaded = HexBatState.read(tag, BAT_ID, NOW);
        assertEquals(Provenance.UNBOUND, loaded.provenance());
        assertTrue(loaded.anchor().isEmpty());
    }

    @Test
    void unknownEnumNamesAndMalformedIdentifiersDegradeSafely() {
        final CompoundTag tag = populated().write();
        tag.putString("Provenance", "haunted_nonsense");
        tag.putString("Mode", "warp_drive");
        tag.putString("Threat", "not-a-uuid");
        tag.putString("AnchorDimension", "not a dimension id!");
        final HexBatState loaded = HexBatState.read(tag, BAT_ID, NOW);
        assertEquals(Provenance.UNBOUND, loaded.provenance());
        assertEquals(Mode.SHELTER, loaded.mode());
        assertTrue(loaded.threatId().isEmpty(), "a malformed threat UUID clears the report");
        assertTrue(loaded.anchor().isEmpty(), "a malformed dimension drops the coupled anchor");
        assertEquals(populated().roost(), loaded.roost(),
            "independently valid durable facts survive volatile corruption");
    }

    @Test
    void coupledValidationDropsPositionsWithoutDimensions() {
        final HexBatState state = HexBatState.empty(BAT_ID, NOW)
            .withAnchor(Optional.of(BlockPos.ZERO), Optional.empty());
        assertTrue(state.anchor().isEmpty());
        final HexBatState dimensionOnly = HexBatState.empty(BAT_ID, NOW)
            .withRoost(Optional.empty(), Optional.of(OVERWORLD));
        assertTrue(dimensionOnly.roostDimension().isEmpty());
    }

    @Test
    void nearFutureDeadlinesAreRetainedAndExtremeOnesClampOrReset() {
        final CompoundTag tag = populated().write();
        tag.putLong("RouteBackoffUntil", Long.MAX_VALUE);
        tag.putLong("SwoopCooldownUntil", -44L);
        final HexBatState loaded = HexBatState.read(tag, BAT_ID, NOW);
        assertEquals(NOW + HexBatRules.ROUTE_BACKOFF_TICKS, loaded.deadlines().routeBackoffUntil(),
            "a far-future backoff clamps to the approved remaining duration");
        assertEquals(0L, loaded.deadlines().swoopCooldownUntil(), "negative deadlines reset");
        assertTrue(loaded.deadlines().routeBackoffUntil() < NOW + HexBatRules.MAX_FUTURE_HORIZON_TICKS);
    }

    @Test
    void loadCancelsAnActiveSwoopSoDamageCannotReplay() {
        final HexBatState armed = populated().withAction(
            Action.SWOOP, Optional.of(TARGET_ID), Optional.of(OVERWORLD)
        ).withDeadlines(new HexBatState.Deadlines(
            NOW + 5L, NOW + 45L, 0L, 0L, 0L, 0L, 0L, 0L
        ));
        final HexBatState loaded = HexBatState.read(armed.write(), BAT_ID, NOW);
        assertEquals(Action.NONE, loaded.action(), "load cancels the in-progress action");
        assertTrue(loaded.actionTargetId().isEmpty(), "the immutable identity clears with it");
        assertEquals(0L, loaded.deadlines().actionWindupUntil());
        assertEquals(0L, loaded.deadlines().actionExecuteUntil());
        assertEquals(NOW + HexBatRules.SWOOP_RECOVERY_TICKS, loaded.deadlines().actionRecoverUntil(),
            "cancellation lands in bounded recovery, not a free instant re-attack");
        assertFalse(loaded.mode() == Mode.INTERCEPT, "intercept mode never survives a load");
    }

    @Test
    void immutableActionIdentityIsCoupledAndCannotExistPartially() {
        final HexBatState missingDimension = populated().withAction(
            Action.SWOOP, Optional.of(TARGET_ID), Optional.empty()
        );
        assertEquals(Action.NONE, missingDimension.action());
        final HexBatState cleared = populated().withAction(Action.NONE, Optional.of(TARGET_ID), Optional.of(OVERWORLD));
        assertTrue(cleared.actionTargetId().isEmpty(), "NONE keeps no stale target identity");
    }

    @Test
    void expiredThreatReportsClearOnLoad() {
        final CompoundTag tag = populated().write();
        tag.putLong("ThreatExpiresAt", NOW - 1L);
        final HexBatState loaded = HexBatState.read(tag, BAT_ID, NOW);
        assertTrue(loaded.threatId().isEmpty());
        assertEquals(0L, loaded.threatExpiresAt());
    }

    @Test
    void dimensionCleanupClearsAnchorRoostDestinationActionAndReport() {
        final HexBatState state = populated().withAction(
            Action.SWOOP, Optional.of(TARGET_ID), Optional.of(OVERWORLD)
        ).clearedForDimensionChange();
        assertTrue(state.anchor().isEmpty());
        assertTrue(state.roost().isEmpty());
        assertTrue(state.destination().isEmpty());
        assertEquals(DestinationPurpose.NONE, state.destinationPurpose());
        assertEquals(Action.NONE, state.action());
        assertTrue(state.threatId().isEmpty());
        assertEquals(Provenance.MURDEROUS_FLOCK, state.provenance(), "provenance survives transfer");
    }

    @Test
    void serializedFormContainsNoCollectionPathOrLiveObject() {
        final CompoundTag tag = populated().withAction(
            Action.SWOOP, Optional.of(TARGET_ID), Optional.of(OVERWORLD)
        ).write();
        for (final String key : tag.keySet()) {
            final var value = tag.get(key);
            assertFalse(value instanceof net.minecraft.nbt.ListTag,
                "no serialized list may exist: " + key);
            assertFalse(value instanceof net.minecraft.nbt.CompoundTag,
                "no nested structure may exist: " + key);
        }
    }

    @Test
    void routeFailuresClampToTheApprovedMaximum() {
        assertEquals(HexBatRules.MAX_ROUTE_FAILURES, populated().withRouteFailures(99).routeFailures());
        assertEquals(0, populated().withRouteFailures(-4).routeFailures());
    }
}
