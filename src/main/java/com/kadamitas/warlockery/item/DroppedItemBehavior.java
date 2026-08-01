package com.kadamitas.warlockery.item;

import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;

@FunctionalInterface
public interface DroppedItemBehavior {
    boolean tickDroppedItem(ItemStack stack, ItemEntity entity);
}
