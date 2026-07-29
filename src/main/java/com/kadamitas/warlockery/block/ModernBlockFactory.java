package com.kadamitas.warlockery.block;

import java.util.Map;
import java.util.Optional;
import java.util.Set;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ButtonBlock;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.FenceBlock;
import net.minecraft.world.level.block.FenceGateBlock;
import net.minecraft.world.level.block.LadderBlock;
import net.minecraft.world.level.block.PressurePlateBlock;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.properties.BlockSetType;
import net.minecraft.world.level.block.state.properties.WoodType;
import net.minecraft.world.level.material.PushReaction;

public final class ModernBlockFactory {
    private static final Map<String, Specification> SPECIFICATIONS = Map.ofEntries(
        entry("alderwooddoor", Shape.DOOR, Family.WOOD),
        entry("rowanwooddoor", Shape.DOOR, Family.WOOD),
        entry("cwoodendoor", Shape.DOOR, Family.WOOD),
        entry("icedoor", Shape.DOOR, Family.ICE),
        entry("cbuttonstone", Shape.BUTTON, Family.STONE),
        entry("cbuttonwood", Shape.BUTTON, Family.WOOD),
        entry("cstonepressureplate", Shape.PRESSURE_PLATE, Family.STONE),
        entry("cwoodpressureplate", Shape.PRESSURE_PLATE, Family.WOOD),
        entry("icepressureplate", Shape.PRESSURE_PLATE, Family.ICE),
        entry("snowpressureplate", Shape.PRESSURE_PLATE, Family.SNOW),
        entry("icefence", Shape.FENCE, Family.ICE),
        entry("stockade", Shape.FENCE, Family.WOOD),
        entry("icestockade", Shape.FENCE, Family.ICE),
        entry("icefencegate", Shape.FENCE_GATE, Family.ICE),
        entry("iceslab", Shape.SLAB, Family.ICE),
        entry("icedoubleslab", Shape.SLAB, Family.ICE),
        entry("snowslab", Shape.SLAB, Family.SNOW),
        entry("snowdoubleslab", Shape.SLAB, Family.SNOW),
        entry("hexwoodslab", Shape.SLAB, Family.WOOD),
        entry("hexwooddoubleslab", Shape.SLAB, Family.WOOD),
        entry("icestairs", Shape.STAIRS, Family.ICE),
        entry("snowstairs", Shape.STAIRS, Family.SNOW),
        entry("stairswoodalder", Shape.STAIRS, Family.WOOD),
        entry("stairswoodhawthorn", Shape.STAIRS, Family.WOOD),
        entry("stairswoodrowan", Shape.STAIRS, Family.WOOD),
        entry("hex_ladder", Shape.LADDER, Family.WOOD)
    );

    private ModernBlockFactory() {
    }

    public static boolean supports(final String id) {
        return SPECIFICATIONS.containsKey(id);
    }

    public static Optional<Shape> shapeOf(final String id) {
        return Optional.ofNullable(SPECIFICATIONS.get(id)).map(Specification::shape);
    }

    public static Set<String> supportedIds() {
        return SPECIFICATIONS.keySet();
    }

    public static Class<? extends Block> implementationType(final String id) {
        final Specification specification = Optional.ofNullable(SPECIFICATIONS.get(id))
            .orElseThrow(() -> new IllegalArgumentException("Unsupported shaped block: " + id));
        if ("alderwooddoor".equals(id)) {
            return SignalDoorBlock.class;
        }
        if ("rowanwooddoor".equals(id)) {
            return RunedDoorBlock.class;
        }
        return switch (specification.shape()) {
            case BUTTON -> ButtonBlock.class;
            case DOOR -> DoorBlock.class;
            case FENCE -> FenceBlock.class;
            case FENCE_GATE -> FenceGateBlock.class;
            case LADDER -> LadderBlock.class;
            case PRESSURE_PLATE -> PressurePlateBlock.class;
            case SLAB -> SlabBlock.class;
            case STAIRS -> StairBlock.class;
        };
    }

    public static Block create(final String id, final BlockBehaviour.Properties properties) {
        final Specification specification = Optional.ofNullable(SPECIFICATIONS.get(id))
            .orElseThrow(() -> new IllegalArgumentException("Unsupported shaped block: " + id));
        if ("alderwooddoor".equals(id)) {
            return new SignalDoorBlock(properties.noOcclusion().pushReaction(PushReaction.DESTROY));
        }
        if ("rowanwooddoor".equals(id)) {
            return new RunedDoorBlock(properties.noOcclusion().pushReaction(PushReaction.DESTROY));
        }
        return switch (specification.shape()) {
            case BUTTON -> new ButtonBlock(specification.blockSetType(), specification.family() == Family.WOOD ? 30 : 20,
                properties.noCollision().pushReaction(PushReaction.DESTROY));
            case DOOR -> new DoorBlock(specification.blockSetType(),
                properties.noOcclusion().pushReaction(PushReaction.DESTROY));
            case FENCE -> new FenceBlock(properties);
            case FENCE_GATE -> new FenceGateBlock(WoodType.OAK, properties);
            case LADDER -> new LadderBlock(
                properties.noOcclusion().sound(SoundType.LADDER).pushReaction(PushReaction.DESTROY));
            case PRESSURE_PLATE -> new PressurePlateBlock(specification.blockSetType(),
                properties.noCollision().pushReaction(PushReaction.DESTROY));
            case SLAB -> new SlabBlock(properties);
            case STAIRS -> new StairBlock(specification.family().baseBlock().defaultBlockState(), properties);
        };
    }

    private static Map.Entry<String, Specification> entry(
        final String id,
        final Shape shape,
        final Family family
    ) {
        return Map.entry(id, new Specification(shape, family));
    }

    public enum Shape {
        BUTTON,
        DOOR,
        FENCE,
        FENCE_GATE,
        LADDER,
        PRESSURE_PLATE,
        SLAB,
        STAIRS
    }

    private enum Family {
        WOOD,
        STONE,
        ICE,
        SNOW;

        private BlockSetType blockSetType() {
            return this == WOOD ? BlockSetType.OAK : BlockSetType.STONE;
        }

        private Block baseBlock() {
            return switch (this) {
                case WOOD -> Blocks.OAK_PLANKS;
                case STONE -> Blocks.STONE;
                case ICE -> Blocks.PACKED_ICE;
                case SNOW -> Blocks.SNOW_BLOCK;
            };
        }
    }

    private record Specification(Shape shape, Family family) {
        private BlockSetType blockSetType() {
            return family.blockSetType();
        }
    }
}
