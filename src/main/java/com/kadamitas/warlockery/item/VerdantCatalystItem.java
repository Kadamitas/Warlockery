package com.kadamitas.warlockery.item;

import com.kadamitas.warlockery.registry.ModBlocks;
import com.kadamitas.warlockery.dream.SpiritWorldRuntime;
import java.util.List;
import java.util.stream.Stream;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.BlockItemTags;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

public final class VerdantCatalystItem extends Item {
    private static final List<String> TRANSFORMATIONS = List.of(
        "embermoss", "glintweed", "leapinglily", "spanishmoss", "hex_sapling", "hex_leaves",
        "bramble", "bloodrose", "crittersnare", "grassper", "somniancotton"
    );

    private final boolean prime;

    public VerdantCatalystItem(final Properties properties, final boolean prime) {
        super(properties);
        this.prime = prime;
    }

    @Override
    public InteractionResult useOn(final UseOnContext context) {
        final BlockState state = context.getLevel().getBlockState(context.getClickedPos());
        if (prime
            && state.is(Blocks.DIRT)
            && !context.getLevel().getFluidState(context.getClickedPos().above()).is(net.minecraft.tags.FluidTags.WATER)) {
            return InteractionResult.PASS;
        }
        if (!canTransform(state, prime)) {
            return InteractionResult.PASS;
        }
        if (!context.getLevel().isClientSide()) {
            final Block replacement = directTransformation(state, context).orElseGet(() -> {
                final List<String> transformations = transformations(SpiritWorldRuntime.isSpiritWorld(context.getLevel()));
                final String chosen = transformations.get(context.getLevel().getRandom().nextInt(transformations.size()));
                return "minecraft:nether_wart".equals(chosen) ? Blocks.NETHER_WART : ModBlocks.ALL.get(chosen).get();
            });
            context.getLevel().setBlockAndUpdate(context.getClickedPos(), replacement.defaultBlockState());
            if (context.getPlayer() == null || !context.getPlayer().hasInfiniteMaterials()) {
                context.getItemInHand().shrink(1);
            }
        }
        return InteractionResult.SUCCESS;
    }

    static List<String> transformations(final boolean spiritWorld) {
        return spiritWorld
            ? Stream.concat(TRANSFORMATIONS.stream(), Stream.of("minecraft:nether_wart")).toList()
            : TRANSFORMATIONS;
    }

    static boolean canTransform(final BlockState state, final boolean prime) {
        return state.is(BlockTags.FLOWERS)
            || state.is(BlockItemTags.SAPLINGS.block())
            || state.is(BlockTags.LEAVES)
            || prime && (state.getBlock() instanceof CropBlock
                || state.is(Blocks.GRASS_BLOCK)
                || state.is(Blocks.MYCELIUM)
                || state.is(Blocks.DIRT));
    }

    private java.util.Optional<Block> directTransformation(final BlockState state, final UseOnContext context) {
        if (!prime) {
            return java.util.Optional.empty();
        }
        if (state.is(Blocks.GRASS_BLOCK)) {
            return java.util.Optional.of(Blocks.MYCELIUM);
        }
        if (state.is(Blocks.MYCELIUM)) {
            return java.util.Optional.of(Blocks.GRASS_BLOCK);
        }
        if (state.is(Blocks.DIRT) && context.getLevel().getFluidState(context.getClickedPos().above()).is(net.minecraft.tags.FluidTags.WATER)) {
            return java.util.Optional.of(Blocks.CLAY);
        }
        return java.util.Optional.empty();
    }
}
