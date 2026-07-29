package com.kadamitas.warlockery.item;

import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;

public record MirrorState(Identifier dimension, BlockPos position) {
    private static final String DIMENSION = "WarlockeryMirrorDimension";
    private static final String POSITION = "WarlockeryMirrorPosition";

    public static Optional<MirrorState> read(final ItemStack stack) {
        return read(stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag());
    }

    static Optional<MirrorState> read(final CompoundTag data) {
        final String dimension = data.getStringOr(DIMENSION, "");
        return data.getLong(POSITION).flatMap(position -> {
            try {
                return Optional.of(new MirrorState(Identifier.parse(dimension), BlockPos.of(position)));
            } catch (IllegalArgumentException exception) {
                return Optional.empty();
            }
        });
    }

    public void write(final ItemStack stack) {
        CustomData.update(DataComponents.CUSTOM_DATA, stack, data -> {
            data.putString(DIMENSION, dimension.toString());
            data.putLong(POSITION, position.asLong());
        });
    }
}
