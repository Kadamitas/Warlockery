package com.kadamitas.warlockery.block;

import static org.junit.jupiter.api.Assertions.assertEquals;

import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/** Dispatch regression only; rendered client acceptance verifies readings, memory and travel. */
final class PlacedMirrorEmptyHandDispatchTest {
    @BeforeAll
    static void bootstrapMinecraftRegistries() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void emptyStackUsesTheReadingPathWithoutResolvingAnyMirrorTool() throws Exception {
        final var dispatch = InteractiveUtilityBlock.class.getDeclaredMethod("bindMirror",
            Level.class, BlockPos.class, Player.class, ItemStack.class);
        dispatch.setAccessible(true);
        // Reading acknowledges non-server contexts before looking at world/player state.
        // Tool binding would instead resolve mod items and reject this empty stack.
        assertEquals(InteractionResult.SUCCESS, dispatch.invoke(null, null, BlockPos.ZERO, null, ItemStack.EMPTY));
    }
}
