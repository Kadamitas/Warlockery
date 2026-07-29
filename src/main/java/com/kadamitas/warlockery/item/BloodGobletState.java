package com.kadamitas.warlockery.item;

import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;

public final class BloodGobletState {
    private static final String FULL = "WarlockeryGobletFull";

    private BloodGobletState() {
    }

    public static boolean isFull(final ItemStack stack) {
        return isFull(stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag());
    }

    static boolean isFull(final net.minecraft.nbt.CompoundTag data) {
        return data.getBooleanOr(FULL, false);
    }

    public static void setFull(final ItemStack stack, final boolean full) {
        CustomData.update(DataComponents.CUSTOM_DATA, stack, data -> setFull(data, full));
    }

    static void setFull(final net.minecraft.nbt.CompoundTag data, final boolean full) {
        if (full) {
            data.putBoolean(FULL, true);
        } else {
            data.remove(FULL);
        }
    }
}
