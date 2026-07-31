package com.kadamitas.warlockery.brew;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import org.junit.jupiter.api.Test;

final class LegacyBrewTerrainParityTest {
    @Test
    void vinesFollowOneBlockStepsOrTwoWithAToad() {
        assertEquals(1, BrewTerrainRules.vineStepReach(false));
        assertEquals(2, BrewTerrainRules.vineStepReach(true));
        assertEquals(
            Set.of(Direction.NORTH, Direction.SOUTH, Direction.EAST, Direction.WEST),
            Set.copyOf(BrewTerrainRules.wallFaces())
        );
        assertTrue(BrewTerrainRules.vineTraversalOffsets(Direction.NORTH, false)
            .contains(BlockPos.ZERO.above().north()));
        assertTrue(BrewTerrainRules.vineTraversalOffsets(Direction.NORTH, true)
            .contains(BlockPos.ZERO.above().north(2)));
        assertTrue(BrewTerrainRules.vineTraversalOffsets(Direction.NORTH, true)
            .contains(BlockPos.ZERO.below().south(2)));
        assertTrue(BrewTerrainRules.vineTraversalOffsets(Direction.NORTH, true)
            .contains(BlockPos.ZERO.east(2)));
    }

    @Test
    void thornCagesSurroundTargetsAndToadsDoubleTheirHeight() {
        assertEquals(4, BrewTerrainRules.thornCageOffsets(false).size());
        assertEquals(8, BrewTerrainRules.thornCageOffsets(true).size());
        assertTrue(BrewTerrainRules.thornCageOffsets(false).contains(BlockPos.ZERO.north()));
        assertTrue(BrewTerrainRules.thornCageOffsets(true).contains(BlockPos.ZERO.north().above()));
        assertTrue(BrewTerrainRules.thornTrapDuration(true) > BrewTerrainRules.thornTrapDuration(false));
    }

    @Test
    void everyPairOfEnthralledZombiesProducesOneChild() {
        assertEquals(0, BrewTerrainRules.enthralledOffspringCount(0));
        assertEquals(0, BrewTerrainRules.enthralledOffspringCount(1));
        assertEquals(1, BrewTerrainRules.enthralledOffspringCount(2));
        assertEquals(2, BrewTerrainRules.enthralledOffspringCount(5));
    }
}
