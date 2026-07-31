package com.kadamitas.warlockery.block;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import net.minecraft.core.Direction;
import org.junit.jupiter.api.Test;

final class GlintWeedBlockTest {
    @Test
    void undersidePlacementPrefersTheCeiling() {
        assertTrue(GlintWeedPlacementRules.usesCeiling(Direction.DOWN, true, true));
        assertTrue(GlintWeedPlacementRules.usesCeiling(Direction.DOWN, false, true));
    }

    @Test
    void ordinaryPlacementUsesTheFloorWhenAvailable() {
        assertFalse(GlintWeedPlacementRules.usesCeiling(Direction.UP, true, true));
        assertFalse(GlintWeedPlacementRules.usesCeiling(Direction.NORTH, true, true));
    }

    @Test
    void ceilingIsTheFallbackWhenThereIsNoFloor() {
        assertTrue(GlintWeedPlacementRules.usesCeiling(Direction.NORTH, false, true));
        assertFalse(GlintWeedPlacementRules.usesCeiling(Direction.DOWN, false, false));
    }
}
