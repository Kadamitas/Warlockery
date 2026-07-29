package com.kadamitas.warlockery.item;

import com.kadamitas.warlockery.block.AltarChaliceBlock;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.Nullable;

public final class FilledChaliceItem extends BlockItem {
    public FilledChaliceItem(final Block block, final Properties properties) {
        super(block, properties);
    }

    @Override
    protected @Nullable BlockState getPlacementState(final BlockPlaceContext context) {
        final BlockState state = super.getPlacementState(context);
        return state == null ? null : state.setValue(AltarChaliceBlock.FILLED, true);
    }
}
