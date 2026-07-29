package com.kadamitas.warlockery.item;

import java.util.Optional;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;

public final class BiomeNoteState {
    private static final String BIOME = "WarlockeryBiome";

    private BiomeNoteState() {
    }

    public static void write(final ItemStack stack, final Identifier biome) {
        CustomData.update(DataComponents.CUSTOM_DATA, stack, tag -> write(tag, biome));
    }

    public static void write(final CompoundTag tag, final Identifier biome) {
        tag.putString(BIOME, biome.toString());
    }

    public static Optional<Identifier> read(final ItemStack stack) {
        return read(stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag());
    }

    public static Optional<Identifier> read(final CompoundTag tag) {
        final String value = tag.getStringOr(BIOME, "");
        return value.isBlank() ? Optional.empty() : Optional.ofNullable(Identifier.tryParse(value));
    }
}
