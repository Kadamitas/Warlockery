package com.kadamitas.warlockery.item;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.util.Set;
import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;

final class SpiritLocatorRulesTest {
    @Test
    void hollowThreeByThreeRingUsesEveryPositionExceptItsCenter() {
        assertEquals(8, SpiritLocatorRules.ringOffsets().size());
        assertEquals(8, Set.copyOf(SpiritLocatorRules.ringOffsets()).size());
        assertFalse(SpiritLocatorRules.ringOffsets().contains(BlockPos.ZERO));
        SpiritLocatorRules.ringOffsets().forEach(offset ->
            assertEquals(1, Math.max(Math.abs(offset.getX()), Math.abs(offset.getZ()))));
    }

    @Test
    void directionsFollowMinecraftCardinalCoordinates() {
        assertEquals("north", SpiritLocatorRules.directionKey(0, -10));
        assertEquals("northeast", SpiritLocatorRules.directionKey(10, -10));
        assertEquals("east", SpiritLocatorRules.directionKey(10, 0));
        assertEquals("southeast", SpiritLocatorRules.directionKey(10, 10));
        assertEquals("south", SpiritLocatorRules.directionKey(0, 10));
        assertEquals("southwest", SpiritLocatorRules.directionKey(-10, 10));
        assertEquals("west", SpiritLocatorRules.directionKey(-10, 0));
        assertEquals("northwest", SpiritLocatorRules.directionKey(-10, -10));
        assertEquals("here", SpiritLocatorRules.directionKey(0, 0));
    }

    @Test
    void distanceIgnoresHeightAndRoundsToTheNearestBlock() {
        assertEquals(5, SpiritLocatorRules.horizontalDistance(BlockPos.ZERO, new BlockPos(3, 200, 4)));
    }
}
