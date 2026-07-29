package com.kadamitas.warlockery.util;

import java.util.Optional;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.material.Fluid;
import net.minecraftforge.fluids.FluidStack;

public record FluidIngredient(String value, Identifier id, boolean tag) {
    public static Optional<FluidIngredient> parse(final String value) {
        if (value == null || value.isBlank()) {
            return Optional.empty();
        }
        final boolean tag = value.startsWith("#");
        final Identifier id = Identifier.tryParse(tag ? value.substring(1) : value);
        return id == null ? Optional.empty() : Optional.of(new FluidIngredient(value, id, tag));
    }

    public boolean matches(final FluidStack stack) {
        if (stack.isEmpty()) {
            return false;
        }
        if (tag) {
            return BuiltInRegistries.FLUID.wrapAsHolder(stack.getFluid()).is(TagKey.create(Registries.FLUID, id));
        }
        final Fluid fluid = BuiltInRegistries.FLUID.getValue(id);
        return fluid != null && stack.getFluid() == fluid;
    }

    public boolean isResolvable() {
        return tag || BuiltInRegistries.FLUID.containsKey(id);
    }
}
