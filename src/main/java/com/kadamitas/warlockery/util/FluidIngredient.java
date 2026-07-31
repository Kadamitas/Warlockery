package com.kadamitas.warlockery.util;

import java.util.Optional;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.material.Fluid;
import net.minecraftforge.fluids.FluidStack;

public record FluidIngredient(String value, Identifier id, boolean tag) implements RegistryIngredient {
    public static Optional<FluidIngredient> parse(final String value) {
        return RegistryIngredient.parse(value, FluidIngredient::new);
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
        return isResolvableIn(BuiltInRegistries.FLUID);
    }
}
