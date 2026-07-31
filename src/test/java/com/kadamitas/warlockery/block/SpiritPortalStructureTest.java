package com.kadamitas.warlockery.block;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import org.junit.jupiter.api.Test;

final class SpiritPortalStructureTest {
    @Test
    void archivedFourByFourSnowRingHasAConnectedTwoByTwoInterior() {
        final BlockPos base = new BlockPos(7, 20, 11);
        final SpiritPortalStructure.Layout layout = SpiritPortalStructure.layout(base, Direction.EAST);
        assertEquals(8, layout.frame().size());
        assertEquals(4, layout.interior().size());
        assertTrue(layout.interior().contains(base));
        assertTrue(layout.interior().contains(base.east().above()));
    }

    @Test
    void frameCornersRemainOptional() {
        final BlockPos base = BlockPos.ZERO;
        final SpiritPortalStructure.Layout layout = SpiritPortalStructure.layout(base, Direction.SOUTH);
        assertFalse(layout.frame().contains(base.north().below()));
        assertFalse(layout.frame().contains(base.south(2).above(2)));
    }

    @Test
    void portalWidthCannotRunVertically() {
        assertThrows(IllegalArgumentException.class,
            () -> SpiritPortalStructure.layout(BlockPos.ZERO, Direction.UP));
    }
}
