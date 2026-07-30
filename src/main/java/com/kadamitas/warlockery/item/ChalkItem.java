package com.kadamitas.warlockery.item;

import com.kadamitas.warlockery.block.ConnectedGlyphBlock;
import com.kadamitas.warlockery.registry.ModSounds;
import java.util.function.Supplier;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.block.Block;

public final class ChalkItem extends Item {
    private final Supplier<Block> glyph;

    public ChalkItem(final Properties properties, final Supplier<Block> glyph) {
        super(properties);
        this.glyph = glyph;
    }

    @Override
    public InteractionResult useOn(final UseOnContext context) {
        final var target = context.getClickedPos().relative(context.getClickedFace());
        if (!context.getLevel().getBlockState(target).canBeReplaced()) {
            return InteractionResult.FAIL;
        }
        final Block block = glyph.get();
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
