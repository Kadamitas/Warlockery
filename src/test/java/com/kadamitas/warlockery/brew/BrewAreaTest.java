package com.kadamitas.warlockery.brew;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Set;
import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;

final class BrewAreaTest {
    @Test
    void sphereUsesEuclideanRadius() {
        final BlockPos center = new BlockPos(4, 8, 12);
        final Set<BlockPos> positions = BrewArea.sphere(center, 1).collect(java.util.stream.Collectors.toSet());
        assertEquals(7, positions.size());
        assertTrue(positions.contains(center));
        assertTrue(positions.contains(center.above()));
        assertTrue(positions.contains(center.east()));
    }

    @Test
    void connectedTraversalIncludesDiagonalMembersAndExcludesGaps() {
        final BlockPos origin = BlockPos.ZERO;
        final Set<BlockPos> accepted = Set.of(origin, origin.east(), origin.east().above(), origin.offset(4, 0, 0));
        assertEquals(
            Set.of(origin, origin.east(), origin.east().above()),
            Set.copyOf(BrewArea.connected(origin, 32, accepted::contains))
        );
    }

    @Test
    void connectedTraversalHonorsItsSafetyLimit() {
        final BlockPos origin = BlockPos.ZERO;
        assertEquals(2, BrewArea.connected(origin, 2, ignored -> true).size());
        assertTrue(BrewArea.connected(origin, 0, ignored -> true).isEmpty());
    }
}
