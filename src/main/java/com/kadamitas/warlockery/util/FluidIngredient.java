package com.kadamitas.warlockery.util;

import java.util.Optional;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.material.Fluid;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.transfer.fluid.FluidResource;

public record FluidIngredient(String value, Identifier id, boolean tag) implements RegistryIngredient {
    public static Optional<FluidIngredient> parse(final String value) {
        return RegistryIngredient.parse(value, FluidIngredient::new);
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
        return isResolvableIn(BuiltInRegistries.FLUID);
    }
}
