package com.kadamitas.warlockery.util;

import java.util.Optional;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.material.Fluid;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.transfer.fluid.FluidResource;

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
        return !stack.isEmpty() && matches(stack.getFluid());
    }

    public boolean matches(final FluidResource resource) {
        return !resource.isEmpty() && matches(resource.getFluid());
    }

    private boolean matches(final Fluid candidate) {
        if (tag) {
            return BuiltInRegistries.FLUID.wrapAsHolder(candidate).is(TagKey.create(Registries.FLUID, id));
        }
        final Fluid fluid = BuiltInRegistries.FLUID.getValue(id);
        return fluid != null && candidate == fluid;
    }

    public boolean isResolvable() {
        return tag || BuiltInRegistries.FLUID.containsKey(id);
    }
}
