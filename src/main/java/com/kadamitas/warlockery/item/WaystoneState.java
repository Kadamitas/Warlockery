package com.kadamitas.warlockery.item;

import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;

public final class WaystoneState {
    private static final String DIMENSION = "WarlockeryDimension";
    private static final String POSITION = "WarlockeryWaystonePos";

    private WaystoneState() {
    }

    public static Optional<Location> read(final ItemStack stack) {
        return read(stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag());
    }

    public static Optional<Location> read(final CompoundTag data) {
        final Identifier dimension = Identifier.tryParse(data.getStringOr(DIMENSION, ""));
        if (dimension == null || !data.contains(POSITION)) {
            return Optional.empty();
        }
        return Optional.of(new Location(
            dimension,
            BlockPos.of(data.getLongOr(POSITION, BlockPos.ZERO.asLong()))
        ));
    }

    public static void write(final ItemStack stack, final Identifier dimension, final BlockPos position) {
        CustomData.update(DataComponents.CUSTOM_DATA, stack, data -> write(data, dimension, position));
    }

    public static void write(final CompoundTag data, final Identifier dimension, final BlockPos position) {
        data.putString(DIMENSION, dimension.toString());
        data.putLong(POSITION, position.asLong());
    }

    public record Location(Identifier dimension, BlockPos position) {
    }
}
