package com.kadamitas.warlockery.block;

import java.util.Optional;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SaplingBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.TintedParticleLeavesBlock;
import net.minecraft.world.level.block.VineBlock;
import net.minecraft.world.level.block.grower.TreeGrower;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;

/** Native plant behavior for existing IDs outside the regular wood-family naming scheme. */
public final class LegacyPlantBlockFactory {
    private static final TreeGrower HEX_TREE = new TreeGrower("warlockery_hex", Optional.empty(),
        Optional.of(ResourceKey.create(Registries.CONFIGURED_FEATURE,
            Identifier.fromNamespaceAndPath("warlockery", "hex_tree"))), Optional.empty());

    private LegacyPlantBlockFactory() { }

    public static boolean supports(final String id) {
        return id.equals("hex_sapling") || id.equals("hex_leaves") || id.equals("vine");
    }

    public static Block create(final String id, final BlockBehaviour.Properties properties) {
        properties.mapColor(MapColor.PLANT).noOcclusion().randomTicks().sound(SoundType.GRASS);
        return switch (id) {
            case "hex_sapling" -> new SaplingBlock(HEX_TREE, properties.noCollision().instabreak()
                .pushReaction(PushReaction.DESTROY));
            case "hex_leaves" -> new TintedParticleLeavesBlock(0.01F, properties.strength(0.2F).ignitedByLava());
            case "vine" -> new VineBlock(properties.noCollision().instabreak().ignitedByLava()
                .pushReaction(PushReaction.DESTROY));
            default -> throw new IllegalArgumentException("Unsupported legacy plant: " + id);
        };
    }
}
