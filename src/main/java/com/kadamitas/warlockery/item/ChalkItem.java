package com.kadamitas.warlockery.item;

import com.kadamitas.warlockery.block.ConnectedGlyphBlock;
import com.kadamitas.warlockery.registry.ModBlocks;
import com.kadamitas.warlockery.registry.ModSounds;
import java.util.function.Function;
import java.util.function.Supplier;
import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

public final class ChalkItem extends Item {
    private final Function<UseOnContext, Block> glyph;

    public ChalkItem(final Properties properties, final Supplier<Block> glyph) {
        this(properties, _ -> glyph.get());
    }

    public ChalkItem(final Properties properties, final Function<UseOnContext, Block> glyph) {
        super(properties);
        this.glyph = glyph;
    }

    @Override
    public InteractionResult useOn(final UseOnContext context) {
        final Block block = glyph.apply(context);
        final BlockState clickedState = context.getLevel().getBlockState(context.getClickedPos());
        final boolean replacingGlyph = clickedState.getBlock() instanceof ConnectedGlyphBlock;
        if (replacingGlyph && (clickedState.is(ModBlocks.ALL.get("circle").get()) || clickedState.is(block))) {
            return InteractionResult.FAIL;
        }
        final BlockPos target = replacingGlyph
            ? context.getClickedPos()
            : context.getClickedPos().relative(context.getClickedFace());
        if (!replacingGlyph && !context.getLevel().getBlockState(target).canBeReplaced()) {
            return InteractionResult.FAIL;
        }
        final var placement = block instanceof ConnectedGlyphBlock connected
            ? connected.connectedState(context.getLevel(), target)
            : block.defaultBlockState();
        if (!placement.canSurvive(context.getLevel(), target)) {
            return InteractionResult.FAIL;
        }
        if (!context.getLevel().isClientSide()) {
            context.getLevel().setBlockAndUpdate(target, placement);
            context.getLevel().playSound(null, target, ModSounds.CHALK.get(), SoundSource.BLOCKS, 0.7F, 0.9F + context.getLevel().getRandom().nextFloat() * 0.2F);
            if (context.getPlayer() != null && !context.getPlayer().hasInfiniteMaterials()) {
                context.getItemInHand().hurtAndBreak(1, context.getPlayer(), context.getHand());
            }
        }
        return InteractionResult.SUCCESS;
    }
}
