package com.kadamitas.warlockery.item;

import com.kadamitas.warlockery.registry.ModBlocks;
import java.util.List;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.BlockItemTags;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.state.BlockState;

public final class VerdantCatalystItem extends Item {
    private static final List<String> TRANSFORMATIONS = List.of(
        "embermoss", "glintweed", "leapinglily", "spanishmoss", "hex_sapling", "hex_leaves",
        "bramble", "bloodrose", "crittersnare", "grassper", "somniancotton"
    );

    public VerdantCatalystItem(final Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult useOn(final UseOnContext context) {
        final BlockState state = context.getLevel().getBlockState(context.getClickedPos());
        if (!canTransform(state)) {
            return InteractionResult.PASS;
        }
        if (!context.getLevel().isClientSide()) {
            final String chosen = TRANSFORMATIONS.get(context.getLevel().getRandom().nextInt(TRANSFORMATIONS.size()));
            final Block replacement = ModBlocks.ALL.get(chosen).get();
            context.getLevel().setBlockAndUpdate(context.getClickedPos(), replacement.defaultBlockState());
            if (context.getPlayer() == null || !context.getPlayer().hasInfiniteMaterials()) {
                context.getItemInHand().shrink(1);
            }
        }
        return InteractionResult.SUCCESS;
    }

    private static boolean canTransform(final BlockState state) {
        return state.is(BlockTags.FLOWERS)
            || state.is(BlockItemTags.SAPLINGS.block())
            || state.is(BlockTags.LEAVES)
            || state.getBlock() instanceof CropBlock;
    }
}
