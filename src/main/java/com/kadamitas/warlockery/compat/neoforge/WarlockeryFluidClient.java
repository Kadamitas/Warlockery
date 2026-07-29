package com.kadamitas.warlockery.compat.neoforge;

import com.kadamitas.warlockery.registry.ModFluids;
import net.minecraft.client.renderer.block.FluidModel;
import net.minecraft.client.resources.model.sprite.Material;
import net.minecraft.resources.Identifier;
import net.neoforged.neoforge.client.event.RegisterFluidModelsEvent;
import net.neoforged.neoforge.client.fluid.FluidTintSources;

public final class WarlockeryFluidClient {
    private WarlockeryFluidClient() {
    }

    public static void registerModels(final RegisterFluidModelsEvent event) {
        event.register(
            model("warlockery:block/flowspirit_still", "warlockery:block/flowspirit_flow", 0xFFD4C7FF),
            ModFluids.SPIRIT_SOURCE,
            ModFluids.FLOWING_SPIRIT
        );
        event.register(
            model("warlockery:block/flowspirit_still", "warlockery:block/flowspirit_flow", 0xFF20285C),
            ModFluids.HOLLOW_TEARS_SOURCE,
            ModFluids.FLOWING_HOLLOW_TEARS
        );
        event.register(
            model("minecraft:block/water_still", "minecraft:block/water_flow", 0xFF9A4FC3),
            ModFluids.COLORED_BREW_WATER_SOURCE,
            ModFluids.FLOWING_COLORED_BREW_WATER
        );
        event.register(
            model("minecraft:block/water_still", "minecraft:block/water_flow", 0xFFA1C84C),
            ModFluids.EROSION_SOURCE,
            ModFluids.FLOWING_EROSION
        );
    }

    private static FluidModel.Unbaked model(final String still, final String flowing, final int tint) {
        return new FluidModel.Unbaked(
            new Material(texture(still)),
            new Material(texture(flowing)),
            null,
            FluidTintSources.constant(tint)
        );
    }

    private static Identifier texture(final String id) {
        return Identifier.parse(id);
    }
}
