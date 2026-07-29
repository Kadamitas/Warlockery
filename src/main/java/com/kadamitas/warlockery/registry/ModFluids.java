package com.kadamitas.warlockery.registry;

import com.kadamitas.warlockery.Warlockery;
import java.util.function.Consumer;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.level.material.FlowingFluid;
import net.minecraft.world.level.material.Fluid;
import net.minecraftforge.client.extensions.common.IClientFluidTypeExtensions;
import net.minecraftforge.common.SoundActions;
import net.minecraftforge.fluids.FluidType;
import net.minecraftforge.fluids.ForgeFlowingFluid;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class ModFluids {
    public static final DeferredRegister<FluidType> TYPES = DeferredRegister.create(
        ForgeRegistries.Keys.FLUID_TYPES,
        Warlockery.MOD_ID
    );
    public static final DeferredRegister<Fluid> REGISTRY = DeferredRegister.create(ForgeRegistries.FLUIDS, Warlockery.MOD_ID);
    public static final RegistryObject<FluidType> SPIRIT_TYPE = TYPES.register("spirit", SpiritFluidType::new);
    public static final RegistryObject<FluidType> HOLLOW_TEARS_TYPE = TYPES.register(
        "hollow_tears",
        HollowTearsFluidType::new
    );
    public static final RegistryObject<FluidType> COLORED_BREW_WATER_TYPE = TYPES.register(
        "colored_brew_water",
        () -> new ArcaneBrewFluidType("colored_brew_water", 0xFF9A4FC3, 1_000, 1_100)
    );
    public static final RegistryObject<FluidType> EROSION_TYPE = TYPES.register(
        "erosion_brew",
        () -> new ArcaneBrewFluidType("erosion_brew", 0xFFA1C84C, 1_180, 1_800)
    );
    public static final RegistryObject<FlowingFluid> SPIRIT_SOURCE = REGISTRY.register(
        "spirit",
        () -> new ForgeFlowingFluid.Source(spiritProperties())
    );
    public static final RegistryObject<FlowingFluid> FLOWING_SPIRIT = REGISTRY.register(
        "flowing_spirit",
        () -> new ForgeFlowingFluid.Flowing(spiritProperties())
    );
    public static final RegistryObject<FlowingFluid> HOLLOW_TEARS_SOURCE = REGISTRY.register(
        "hollow_tears",
        () -> new ForgeFlowingFluid.Source(hollowTearsProperties())
    );
    public static final RegistryObject<FlowingFluid> FLOWING_HOLLOW_TEARS = REGISTRY.register(
        "flowing_hollow_tears",
        () -> new ForgeFlowingFluid.Flowing(hollowTearsProperties())
    );
    public static final RegistryObject<FlowingFluid> COLORED_BREW_WATER_SOURCE = REGISTRY.register(
        "colored_brew_water",
        () -> new ForgeFlowingFluid.Source(coloredBrewWaterProperties())
    );
    public static final RegistryObject<FlowingFluid> FLOWING_COLORED_BREW_WATER = REGISTRY.register(
        "flowing_colored_brew_water",
        () -> new ForgeFlowingFluid.Flowing(coloredBrewWaterProperties())
    );
    public static final RegistryObject<FlowingFluid> EROSION_SOURCE = REGISTRY.register(
        "erosion_brew",
        () -> new ForgeFlowingFluid.Source(erosionProperties())
    );
    public static final RegistryObject<FlowingFluid> FLOWING_EROSION = REGISTRY.register(
        "flowing_erosion_brew",
        () -> new ForgeFlowingFluid.Flowing(erosionProperties())
    );

    private ModFluids() {
    }

    private static ForgeFlowingFluid.Properties spiritProperties() {
        return properties(SPIRIT_TYPE, SPIRIT_SOURCE, FLOWING_SPIRIT, "bucketspirit", "spiritflowing")
            .tickRate(8)
            .slopeFindDistance(3)
            .levelDecreasePerBlock(2)
            .explosionResistance(10.0F);
    }

    private static ForgeFlowingFluid.Properties hollowTearsProperties() {
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

    private static ForgeFlowingFluid.Properties coloredBrewWaterProperties() {
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

    private static ForgeFlowingFluid.Properties erosionProperties() {
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

    private static ForgeFlowingFluid.Properties properties(
        final RegistryObject<FluidType> type,
        final RegistryObject<FlowingFluid> source,
        final RegistryObject<FlowingFluid> flowing,
        final String bucket,
        final String block
    ) {
        return new ForgeFlowingFluid.Properties(type, source, flowing)
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

        @Override
        public void initializeClient(final Consumer<IClientFluidTypeExtensions> consumer) {
            consumer.accept(new IClientFluidTypeExtensions() {
                @Override
                public Identifier getStillTexture() {
                    return Identifier.fromNamespaceAndPath(Warlockery.MOD_ID, "block/flowspirit_still");
                }

                @Override
                public Identifier getFlowingTexture() {
                    return Identifier.fromNamespaceAndPath(Warlockery.MOD_ID, "block/flowspirit_flow");
                }

                @Override
                public int getTintColor() {
                    return 0xFFD4C7FF;
                }
            });
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

        @Override
        public void initializeClient(final Consumer<IClientFluidTypeExtensions> consumer) {
            consumer.accept(new IClientFluidTypeExtensions() {
                @Override
                public Identifier getStillTexture() {
                    return Identifier.fromNamespaceAndPath(Warlockery.MOD_ID, "block/flowspirit_still");
                }

                @Override
                public Identifier getFlowingTexture() {
                    return Identifier.fromNamespaceAndPath(Warlockery.MOD_ID, "block/flowspirit_flow");
                }

                @Override
                public int getTintColor() {
                    return 0xFF20285C;
                }
            });
        }
    }

    private static final class ArcaneBrewFluidType extends FluidType {
        private final int tint;

        private ArcaneBrewFluidType(
            final String id,
            final int tint,
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
            this.tint = tint;
        }

        @Override
        public void initializeClient(final Consumer<IClientFluidTypeExtensions> consumer) {
            consumer.accept(new IClientFluidTypeExtensions() {
                @Override
                public Identifier getStillTexture() {
                    return Identifier.fromNamespaceAndPath("minecraft", "block/water_still");
                }

                @Override
                public Identifier getFlowingTexture() {
                    return Identifier.fromNamespaceAndPath("minecraft", "block/water_flow");
                }

                @Override
                public int getTintColor() {
                    return tint;
                }
            });
        }
    }
}
