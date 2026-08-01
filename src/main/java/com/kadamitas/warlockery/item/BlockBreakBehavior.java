package com.kadamitas.warlockery.item;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

@FunctionalInterface
public interface BlockBreakBehavior {
    boolean beforeBlockBreak(ItemStack stack, BlockPos position, Player player);
}
