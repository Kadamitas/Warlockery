package com.kadamitas.warlockery.block;

import java.util.Optional;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;

public final class FetishBindingState {
    private static final String MODE = "WarlockeryBoundFetishMode";

    private FetishBindingState() {
    }

    public static Optional<FetishMode> read(final ItemStack stack) {
        return read(stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag());
    }

    static Optional<FetishMode> read(final CompoundTag data) {
        final String id = data.getStringOr(MODE, "");
        return FetishMode.VALUES.stream().filter(mode -> mode.getSerializedName().equals(id)).findFirst();
    }

    public static void write(final ItemStack stack, final FetishMode mode) {
        CustomData.update(DataComponents.CUSTOM_DATA, stack, data -> write(data, mode));
    }

    static void write(final CompoundTag data, final FetishMode mode) {
        data.putString(MODE, mode.getSerializedName());
    }
}
