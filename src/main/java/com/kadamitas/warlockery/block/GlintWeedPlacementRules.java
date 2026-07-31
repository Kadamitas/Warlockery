package com.kadamitas.warlockery.block;

import net.minecraft.core.Direction;

final class GlintWeedPlacementRules {
    private GlintWeedPlacementRules() {
    }

    static boolean usesCeiling(final Direction clickedFace, final boolean floorSupported, final boolean ceilingSupported) {
        return ceilingSupported && (clickedFace == Direction.DOWN || !floorSupported);
    }
}
