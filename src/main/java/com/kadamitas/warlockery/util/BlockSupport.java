package com.kadamitas.warlockery.util;

import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/** Preserves pre-26.3 motion-blocking semantics for existing landing and teleport rules. */
public final class BlockSupport {
    // The new teleport predicate rejects blocks; it is not the landing-support predicate.
    // Keep the old spells' lack of additional avoidance rules. Vanilla still checks
    // landing support, collisions, liquids, and the world border.
    public static final java.util.function.Predicate<BlockState> NO_ADDITIONAL_TELEPORT_AVOIDANCE = state -> false;

    private BlockSupport() {}

    public static boolean blocksMotion(final BlockState state) {
        return !state.is(Blocks.COBWEB) && !state.is(Blocks.BAMBOO_SAPLING) && state.isSolid();
    }
}
