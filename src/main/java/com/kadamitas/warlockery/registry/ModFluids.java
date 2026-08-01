package com.kadamitas.warlockery.registry;

import com.kadamitas.warlockery.Warlockery;
import java.util.List;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.material.FlowingFluid;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.FluidState;

public final class ModFluids {
    public static final RegistrationHandle<FlowingFluid> SPIRIT_SOURCE = source("spirit", FluidKind.SPIRIT);
    public static final RegistrationHandle<FlowingFluid> FLOWING_SPIRIT = flowing("flowing_spirit", FluidKind.SPIRIT);
    public static final RegistrationHandle<FlowingFluid> HOLLOW_TEARS_SOURCE = source(
        "hollow_tears",
        FluidKind.HOLLOW_TEARS
    );
    public static final RegistrationHandle<FlowingFluid> FLOWING_HOLLOW_TEARS = flowing(
        "flowing_hollow_tears",
        FluidKind.HOLLOW_TEARS
    );
    public static final RegistrationHandle<FlowingFluid> COLORED_BREW_WATER_SOURCE = source(
        "colored_brew_water",
        FluidKind.COLORED_BREW_WATER
    );
    public static final RegistrationHandle<FlowingFluid> FLOWING_COLORED_BREW_WATER = flowing(
        "flowing_colored_brew_water",
        FluidKind.COLORED_BREW_WATER
    );
    public static final RegistrationHandle<FlowingFluid> EROSION_SOURCE = source(
        "erosion_brew",
        FluidKind.EROSION
    );
    public static final RegistrationHandle<FlowingFluid> FLOWING_EROSION = flowing(
        "flowing_erosion_brew",
        FluidKind.EROSION
    );
    private static final List<RenderFamily> FAMILIES = List.of(
        new RenderFamily(
            SPIRIT_SOURCE,
            FLOWING_SPIRIT,
            texture("flowspirit_still"),
            texture("flowspirit_flow"),
            0xFFD4C7FF
        ),
        new RenderFamily(
            HOLLOW_TEARS_SOURCE,
            FLOWING_HOLLOW_TEARS,
            texture("flowspirit_still"),
            texture("flowspirit_flow"),
            0xFF20285C
        ),
        new RenderFamily(
            COLORED_BREW_WATER_SOURCE,
            FLOWING_COLORED_BREW_WATER,
            Identifier.withDefaultNamespace("block/water_still"),
            Identifier.withDefaultNamespace("block/water_flow"),
            0xFF9A4FC3
        ),
        new RenderFamily(
            EROSION_SOURCE,
            FLOWING_EROSION,
            Identifier.withDefaultNamespace("block/water_still"),
            Identifier.withDefaultNamespace("block/water_flow"),
            0xFFA1C84C
        )
    );

    private ModFluids() {
    }

    public static void register() {
        FLOWING_SPIRIT.register(BuiltInRegistries.FLUID);
        SPIRIT_SOURCE.register(BuiltInRegistries.FLUID);
        FLOWING_HOLLOW_TEARS.register(BuiltInRegistries.FLUID);
        HOLLOW_TEARS_SOURCE.register(BuiltInRegistries.FLUID);
        FLOWING_COLORED_BREW_WATER.register(BuiltInRegistries.FLUID);
        COLORED_BREW_WATER_SOURCE.register(BuiltInRegistries.FLUID);
        FLOWING_EROSION.register(BuiltInRegistries.FLUID);
        EROSION_SOURCE.register(BuiltInRegistries.FLUID);
    }

    public static List<RenderFamily> families() {
        return FAMILIES;
    }

    private static RegistrationHandle<FlowingFluid> source(final String id, final FluidKind kind) {
        return RegistrationHandle.create(id, () -> new Source(kind));
    }

    private static RegistrationHandle<FlowingFluid> flowing(final String id, final FluidKind kind) {
        return RegistrationHandle.create(id, () -> new Flowing(kind));
    }

    private static Identifier texture(final String path) {
        return Identifier.fromNamespaceAndPath(Warlockery.MOD_ID, "block/" + path);
    }

    private static RegistrationHandle<FlowingFluid> source(final FluidKind kind) {
        return switch (kind) {
            case SPIRIT -> SPIRIT_SOURCE;
            case HOLLOW_TEARS -> HOLLOW_TEARS_SOURCE;
            case COLORED_BREW_WATER -> COLORED_BREW_WATER_SOURCE;
            case EROSION -> EROSION_SOURCE;
        };
    }

    private static RegistrationHandle<FlowingFluid> flowing(final FluidKind kind) {
        return switch (kind) {
            case SPIRIT -> FLOWING_SPIRIT;
            case HOLLOW_TEARS -> FLOWING_HOLLOW_TEARS;
            case COLORED_BREW_WATER -> FLOWING_COLORED_BREW_WATER;
            case EROSION -> FLOWING_EROSION;
        };
    }

    public record RenderFamily(
        RegistrationHandle<FlowingFluid> source,
        RegistrationHandle<FlowingFluid> flowing,
        Identifier stillTexture,
        Identifier flowingTexture,
        int tint
    ) {
    }

    private enum FluidKind {
        SPIRIT("bucketspirit", "spiritflowing", 8, 3, 2, 10.0F),
        HOLLOW_TEARS("buckethollowtears", "hollowtears", 12, 2, 2, 12.0F),
        COLORED_BREW_WATER("bucketbrew", "brewliquid", 6, 4, 1, 5.0F),
        EROSION("bucketerosionbrew", "erosionbrew", 14, 2, 2, 8.0F);

        private final String bucket;
        private final String block;
        private final int tickDelay;
        private final int slopeDistance;
        private final int dropOff;
        private final float explosionResistance;

        FluidKind(
            final String bucket,
            final String block,
            final int tickDelay,
            final int slopeDistance,
            final int dropOff,
            final float explosionResistance
        ) {
            this.bucket = bucket;
            this.block = block;
            this.tickDelay = tickDelay;
            this.slopeDistance = slopeDistance;
            this.dropOff = dropOff;
            this.explosionResistance = explosionResistance;
        }
    }

    private abstract static class ArcaneFluid extends FlowingFluid {
        private final FluidKind kind;

        private ArcaneFluid(final FluidKind kind) {
            this.kind = kind;
        }

        @Override
        public Fluid getFlowing() {
            return flowing(kind).get();
        }

        @Override
        public Fluid getSource() {
            return source(kind).get();
        }

        @Override
        public boolean isSame(final Fluid fluid) {
            return fluid == getSource() || fluid == getFlowing();
        }

        @Override
        public Item getBucket() {
            return ModItems.ALL.get(kind.bucket).get();
        }

        @Override
        protected boolean canConvertToSource(final ServerLevel level) {
            return false;
        }

        @Override
        protected void beforeDestroyingBlock(final LevelAccessor level, final BlockPos pos, final BlockState state) {
            final BlockEntity blockEntity = state.hasBlockEntity() ? level.getBlockEntity(pos) : null;
            Block.dropResources(state, level, pos, blockEntity);
        }

        @Override
        protected int getSlopeFindDistance(final LevelReader level) {
            return kind.slopeDistance;
        }

        @Override
        public int getDropOff(final LevelReader level) {
            return kind.dropOff;
        }

        @Override
        public int getTickDelay(final LevelReader level) {
            return kind.tickDelay;
        }

        @Override
        public boolean canBeReplacedWith(
            final FluidState state,
            final BlockGetter level,
            final BlockPos pos,
            final Fluid fluid,
            final Direction direction
        ) {
            return direction == Direction.DOWN && !isSame(fluid);
        }

        @Override
        protected float getExplosionResistance() {
            return kind.explosionResistance;
        }

        @Override
        public Optional<SoundEvent> getPickupSound() {
            return Optional.of(SoundEvents.BUCKET_FILL);
        }

        @Override
        protected BlockState createLegacyBlock(final FluidState state) {
            return ModBlocks.ALL.get(kind.block).get().defaultBlockState()
                .setValue(LiquidBlock.LEVEL, getLegacyLevel(state));
        }
    }

    private static final class Flowing extends ArcaneFluid {
        private Flowing(final FluidKind kind) {
            super(kind);
        }

        @Override
        protected void createFluidStateDefinition(final StateDefinition.Builder<Fluid, FluidState> builder) {
            super.createFluidStateDefinition(builder);
            builder.add(LEVEL);
        }

        @Override
        public int getAmount(final FluidState state) {
            return state.getValue(LEVEL);
        }

        @Override
        public boolean isSource(final FluidState state) {
            return false;
        }
    }

    private static final class Source extends ArcaneFluid {
        private Source(final FluidKind kind) {
            super(kind);
        }

        @Override
        public int getAmount(final FluidState state) {
            return 8;
        }

        @Override
        public boolean isSource(final FluidState state) {
            return true;
        }
    }
}
