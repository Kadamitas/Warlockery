package com.kadamitas.warlockery.entity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.IntStream;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

final class WerewolfHunterRuntimeTest {
    @Test
    void candidateRetentionPreseedsWarrantedActorsBeforeGenericCandidates() {
        final UUID event = new UUID(0L, 1L);
        final UUID direct = new UUID(0L, 2L);
        final UUID current = new UUID(0L, 3L);
        final List<UUID> generic = IntStream.range(100, 140)
            .mapToObj(index -> new UUID(1L, index))
            .toList();
        final List<UUID> retained = WerewolfHunterRuntime.retainCandidates(
            Optional.of(event), Optional.of(direct), Optional.of(current), generic
        );
        assertEquals(WerewolfHunterRules.MAX_RETAINED_CANDIDATES, retained.size());
        assertEquals(event, retained.get(0), "the event quarry is retained before any generic candidate");
        assertEquals(direct, retained.get(1), "the direct attacker is retained second");
        assertEquals(current, retained.get(2), "the stable current quarry is retained third");
        assertTrue(retained.containsAll(List.of(event, direct, current)),
            "warranted actors cannot disappear because sixteen unrelated entities came first");
    }

    @Test
    void candidateRetentionDeduplicatesAndHandlesEmptySeeds() {
        final UUID shared = new UUID(0L, 5L);
        final List<UUID> retained = WerewolfHunterRuntime.retainCandidates(
            Optional.of(shared), Optional.of(shared), Optional.empty(), List.of(shared, new UUID(0L, 6L))
        );
        assertEquals(List.of(shared, new UUID(0L, 6L)), retained);
        assertTrue(WerewolfHunterRuntime.retainCandidates(
            Optional.empty(), Optional.empty(), Optional.empty(), List.of()).isEmpty());
    }

    @Test
    void corridorGeometryDetectsActorsOnlyInsideTheAttributedLine() {
        final Vec3 origin = new Vec3(0.0D, 1.0D, 0.0D);
        final Vec3 direction = new Vec3(10.0D, 0.0D, 0.0D);
        assertTrue(WerewolfHunterRuntime.nearCorridorSegment(
            origin, direction, new Vec3(5.0D, 1.0D, 0.5D), 1.5D),
            "an actor half a block off the line blocks the corridor");
        assertFalse(WerewolfHunterRuntime.nearCorridorSegment(
            origin, direction, new Vec3(5.0D, 1.0D, 3.0D), 1.5D),
            "an actor three blocks aside is outside the corridor");
        assertFalse(WerewolfHunterRuntime.nearCorridorSegment(
            origin, direction, new Vec3(-4.0D, 1.0D, 0.0D), 1.5D),
            "an actor behind the shooter is outside the corridor");
        assertFalse(WerewolfHunterRuntime.nearCorridorSegment(
            origin, direction, new Vec3(15.0D, 1.0D, 0.0D), 1.5D),
            "an actor well beyond the quarry is outside the corridor");
        assertFalse(WerewolfHunterRuntime.nearCorridorSegment(
            origin, Vec3.ZERO, new Vec3(0.0D, 1.0D, 0.0D), 1.5D),
            "a degenerate zero-length corridor never blocks");
    }

    @Test
    void searchWaypointsAreBoundedDeterministicAndLocal() {
        final BlockPos locus = new BlockPos(100, 64, -40);
        final UUID hunter = new UUID(7L, 13L);
        final List<BlockPos> waypoints = WerewolfHunterRuntime.searchWaypoints(locus, hunter);
        assertEquals(WerewolfHunterRules.MAX_SEARCH_WAYPOINTS, waypoints.size());
        assertEquals(waypoints, WerewolfHunterRuntime.searchWaypoints(locus, hunter),
            "waypoint claims are deterministic per identity");
        for (final BlockPos waypoint : waypoints) {
            assertTrue(locus.closerThan(waypoint, WerewolfHunterRules.SEARCH_RADIUS + 0.5D),
                "every waypoint stays within the twelve-block search locus");
        }
        assertEquals(waypoints.stream().distinct().count(), waypoints.size(),
            "waypoints do not duplicate a claim");
    }
}
