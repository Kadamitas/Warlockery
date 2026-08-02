package com.kadamitas.warlockery.item;

import java.util.Optional;
import java.util.Arrays;
import java.util.Locale;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
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

    public static Component displayName(final Identifier biome) {
        final String fallback = Arrays.stream(biome.getPath().split("[/_]"))
            .filter(part -> !part.isBlank())
            .map(part -> part.substring(0, 1).toUpperCase(Locale.ROOT) + part.substring(1))
            .reduce((left, right) -> left + " " + right)
            .orElse(biome.toString());
        return Component.translatableWithFallback(biome.toLanguageKey("biome"), fallback);
    }
}
