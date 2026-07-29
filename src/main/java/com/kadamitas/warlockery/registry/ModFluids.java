package com.kadamitas.warlockery.registry;

import com.kadamitas.warlockery.Warlockery;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.level.material.FlowingFluid;
import net.minecraft.world.level.material.Fluid;
import net.neoforged.neoforge.common.SoundActions;
import net.neoforged.neoforge.fluids.BaseFlowingFluid;
import net.neoforged.neoforge.fluids.FluidType;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

public final class ModFluids {
    public static final DeferredRegister<FluidType> TYPES = DeferredRegister.create(
        NeoForgeRegistries.Keys.FLUID_TYPES,
        Warlockery.MOD_ID
    );
    public static final DeferredRegister<Fluid> REGISTRY = DeferredRegister.create(
        BuiltInRegistries.FLUID,
        Warlockery.MOD_ID
    );
    public static final DeferredHolder<FluidType, FluidType> SPIRIT_TYPE = TYPES.register("spirit", SpiritFluidType::new);
    public static final DeferredHolder<FluidType, FluidType> HOLLOW_TEARS_TYPE = TYPES.register(
        "hollow_tears",
        HollowTearsFluidType::new
    );
    public static final DeferredHolder<FluidType, FluidType> COLORED_BREW_WATER_TYPE = TYPES.register(
        "colored_brew_water",
        () -> new ArcaneBrewFluidType("colored_brew_water", 1_000, 1_100)
    );
    public static final DeferredHolder<FluidType, FluidType> EROSION_TYPE = TYPES.register(
        "erosion_brew",
        () -> new ArcaneBrewFluidType("erosion_brew", 1_180, 1_800)
    );
    public static final DeferredHolder<Fluid, FlowingFluid> SPIRIT_SOURCE = REGISTRY.register(
        "spirit",
        () -> new BaseFlowingFluid.Source(spiritProperties())
    );
    public static final DeferredHolder<Fluid, FlowingFluid> FLOWING_SPIRIT = REGISTRY.register(
        "flowing_spirit",
        () -> new BaseFlowingFluid.Flowing(spiritProperties())
    );
    public static final DeferredHolder<Fluid, FlowingFluid> HOLLOW_TEARS_SOURCE = REGISTRY.register(
        "hollow_tears",
        () -> new BaseFlowingFluid.Source(hollowTearsProperties())
    );
    public static final DeferredHolder<Fluid, FlowingFluid> FLOWING_HOLLOW_TEARS = REGISTRY.register(
        "flowing_hollow_tears",
        () -> new BaseFlowingFluid.Flowing(hollowTearsProperties())
    );
    public static final DeferredHolder<Fluid, FlowingFluid> COLORED_BREW_WATER_SOURCE = REGISTRY.register(
        "colored_brew_water",
        () -> new BaseFlowingFluid.Source(coloredBrewWaterProperties())
    );
    public static final DeferredHolder<Fluid, FlowingFluid> FLOWING_COLORED_BREW_WATER = REGISTRY.register(
        "flowing_colored_brew_water",
        () -> new BaseFlowingFluid.Flowing(coloredBrewWaterProperties())
    );
    public static final DeferredHolder<Fluid, FlowingFluid> EROSION_SOURCE = REGISTRY.register(
        "erosion_brew",
        () -> new BaseFlowingFluid.Source(erosionProperties())
    );
    public static final DeferredHolder<Fluid, FlowingFluid> FLOWING_EROSION = REGISTRY.register(
        "flowing_erosion_brew",
        () -> new BaseFlowingFluid.Flowing(erosionProperties())
    );

    private ModFluids() {
    }

    private static BaseFlowingFluid.Properties spiritProperties() {
        return properties(SPIRIT_TYPE, SPIRIT_SOURCE, FLOWING_SPIRIT, "bucketspirit", "spiritflowing")
            .tickRate(8)
            .slopeFindDistance(3)
            .levelDecreasePerBlock(2)
            .explosionResistance(10.0F);
    }

    private static BaseFlowingFluid.Properties hollowTearsProperties() {
        return properties(
            HOLLOW_TEARS_TYPE,
            HOLLOW_TEARS_SOURCE,
            FLOWING_HOLLOW_TEARS,
            "buckethollowtears",
            "hollowtears"
        )
            .tickRate(12)
            .slopeFindDistance(2)
            .levelDecreasePerBlock(2)
            .explosionResistance(12.0F);
    }

    private static BaseFlowingFluid.Properties coloredBrewWaterProperties() {
        return properties(
            COLORED_BREW_WATER_TYPE,
            COLORED_BREW_WATER_SOURCE,
            FLOWING_COLORED_BREW_WATER,
            "bucketbrew",
            "brewliquid"
        )
            .tickRate(6)
            .slopeFindDistance(4)
            .levelDecreasePerBlock(1)
            .explosionResistance(5.0F);
    }

    private static BaseFlowingFluid.Properties erosionProperties() {
        return properties(
            EROSION_TYPE,
            EROSION_SOURCE,
            FLOWING_EROSION,
            "bucketerosionbrew",
            "erosionbrew"
        )
            .tickRate(14)
            .slopeFindDistance(2)
            .levelDecreasePerBlock(2)
            .explosionResistance(8.0F);
    }

    private static BaseFlowingFluid.Properties properties(
        final DeferredHolder<FluidType, FluidType> type,
        final DeferredHolder<Fluid, FlowingFluid> source,
        final DeferredHolder<Fluid, FlowingFluid> flowing,
        final String bucket,
        final String block
    ) {
        return new BaseFlowingFluid.Properties(type, source, flowing)
            .bucket(() -> ModItems.ALL.get(bucket).get())
            .block(() -> (net.minecraft.world.level.block.LiquidBlock) ModBlocks.ALL.get(block).get());
    }

    private static final class SpiritFluidType extends FluidType {
        private SpiritFluidType() {
            super(Properties.create()
                .descriptionId("fluid_type.warlockery.spirit")
                .rarity(Rarity.UNCOMMON)
                .density(850)
                .viscosity(1_400)
                .lightLevel(4)
                .canDrown(false)
                .sound(SoundActions.BUCKET_FILL, SoundEvents.BUCKET_FILL)
                .sound(SoundActions.BUCKET_EMPTY, SoundEvents.BUCKET_EMPTY));
        }
    }

    private static final class HollowTearsFluidType extends FluidType {
        private HollowTearsFluidType() {
            super(Properties.create()
                .descriptionId("fluid_type.warlockery.hollow_tears")
                .rarity(Rarity.UNCOMMON)
                .density(1_100)
                .viscosity(1_600)
                .lightLevel(2)
                .canDrown(false)
                .sound(SoundActions.BUCKET_FILL, SoundEvents.BUCKET_FILL)
                .sound(SoundActions.BUCKET_EMPTY, SoundEvents.BUCKET_EMPTY));
        }
    }

    private static final class ArcaneBrewFluidType extends FluidType {
        private ArcaneBrewFluidType(
            final String id,
            final int density,
            final int viscosity
        ) {
            super(Properties.create()
                .descriptionId("fluid_type.warlockery." + id)
                .rarity(Rarity.UNCOMMON)
                .density(density)
                .viscosity(viscosity)
                .canDrown(false)
                .sound(SoundActions.BUCKET_FILL, SoundEvents.BUCKET_FILL)
                .sound(SoundActions.BUCKET_EMPTY, SoundEvents.BUCKET_EMPTY));
        }
    }
}
