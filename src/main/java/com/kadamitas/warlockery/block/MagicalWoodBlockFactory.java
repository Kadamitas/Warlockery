package com.kadamitas.warlockery.block;

import java.util.Arrays;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.SaplingBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.TintedParticleLeavesBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;

public final class MagicalWoodBlockFactory {
    private static final Map<String, Definition> DEFINITIONS = Arrays.stream(MagicalTreeFamily.values())
        .flatMap(family -> Arrays.stream(MagicalWoodPart.values()).map(part -> new Definition(family, part)))
        .collect(Collectors.toUnmodifiableMap(Definition::id, Function.identity()));

    private MagicalWoodBlockFactory() {
    }

    public static Set<String> ids() {
        return DEFINITIONS.keySet();
    }

    public static boolean supports(final String id) {
        return DEFINITIONS.containsKey(id);
    }

    public static Block create(final String id, final BlockBehaviour.Properties properties) {
        final Definition definition = DEFINITIONS.get(id);
        if (definition == null) {
            throw new IllegalArgumentException("Unsupported magical wood block: " + id);
        }
        return switch (definition.part()) {
            case LOG -> new RotatedPillarBlock(properties
                .mapColor(MapColor.WOOD)
                .strength(2.0F, 2.0F)
                .sound(SoundType.WOOD)
                .ignitedByLava());
            case PLANKS -> new Block(properties
                .mapColor(MapColor.WOOD)
                .strength(2.0F, 3.0F)
                .sound(SoundType.WOOD)
                .ignitedByLava());
            case LEAVES -> new TintedParticleLeavesBlock(0.01F, properties
                .mapColor(MapColor.PLANT)
                .strength(0.2F)
                .randomTicks()
                .noOcclusion()
                .sound(SoundType.GRASS)
                .ignitedByLava());
            case SAPLING -> new SaplingBlock(definition.family().treeGrower(), properties
                .mapColor(MapColor.PLANT)
                .noCollision()
                .noOcclusion()
                .randomTicks()
                .instabreak()
                .sound(SoundType.GRASS)
                .pushReaction(PushReaction.DESTROY));
        };
    }

    private record Definition(MagicalTreeFamily family, MagicalWoodPart part) {
        private String id() {
            return family.blockId(part);
        }
    }
}
