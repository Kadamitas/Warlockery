package com.kadamitas.warlockery.util;

import net.fabricmc.fabric.api.transfer.v1.fluid.FluidConstants;
import net.fabricmc.fabric.api.transfer.v1.fluid.FluidVariant;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.material.Fluid;

public record FluidContents(FluidVariant variant, int milliBuckets) {
    public static final FluidContents EMPTY = new FluidContents(FluidVariant.blank(), 0);

    public FluidContents {
        if (variant == null || milliBuckets < 0) {
            throw new IllegalArgumentException("Fluid contents require a variant and a nonnegative amount");
        }
        if (variant.isBlank() != (milliBuckets == 0)) {
            throw new IllegalArgumentException("Blank fluid contents must have a zero amount");
        }
    }

    public static FluidContents fromDroplets(final FluidVariant variant, final long droplets) {
        if (variant.isBlank() || droplets <= 0) {
            return EMPTY;
        }
        return new FluidContents(variant, milliBucketsFromDroplets(droplets));
    }

    public static long dropletsFromMilliBuckets(final int milliBuckets) {
        if (milliBuckets < 0) {
            throw new IllegalArgumentException("Fluid amount cannot be negative");
        }
        return Math.multiplyExact((long) milliBuckets, FluidConstants.BUCKET) / 1_000L;
    }

    public static int milliBucketsFromDroplets(final long droplets) {
        if (droplets < 0) {
            throw new IllegalArgumentException("Fluid amount cannot be negative");
        }
        return Math.toIntExact(Math.multiplyExact(droplets, 1_000L) / FluidConstants.BUCKET);
    }

    public boolean isEmpty() {
        return variant.isBlank() || milliBuckets == 0;
    }

    public Fluid getFluid() {
        return variant.getFluid();
    }

    public int getAmount() {
        return milliBuckets;
    }

    public boolean is(final TagKey<Fluid> tag) {
        return !isEmpty() && BuiltInRegistries.FLUID.wrapAsHolder(getFluid()).is(tag);
    }

    public String identifier() {
        if (isEmpty()) {
            return "minecraft:empty";
        }
        final Identifier id = BuiltInRegistries.FLUID.getKey(getFluid());
        return id == null ? "minecraft:empty" : id.toString();
    }

    public boolean is(final Identifier tag) {
        return is(TagKey.create(Registries.FLUID, tag));
    }
}
