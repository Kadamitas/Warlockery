package com.kadamitas.warlockery.item;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.tags.TagKey;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.material.Fluid;

public final class FluidDivinerItem extends Item {
    private final TagKey<Fluid> target;
    private final String fluidName;

    public FluidDivinerItem(final Properties properties, final TagKey<Fluid> target, final String fluidName) {
        super(properties);
        this.target = target;
        this.fluidName = fluidName;
    }

    @Override
    public InteractionResult useOn(final UseOnContext context) {
        if (context.getLevel().isClientSide()) {
            return InteractionResult.SUCCESS;
        }
        final BlockPos origin = context.getClickedPos();
        final BlockPos.MutableBlockPos cursor = origin.mutable();
        int depth = 0;
        boolean found = false;
        for (int y = origin.getY(); y >= context.getLevel().getMinY(); y--) {
            cursor.setY(y);
            if (context.getLevel().getFluidState(cursor).is(target)) {
                depth = origin.getY() - y;
                found = true;
                break;
            }
        }
        if (context.getPlayer() != null) {
            context.getPlayer().sendSystemMessage(found
                ? Component.translatable("message.warlockery.diviner.found", fluidName, depth)
                : Component.translatable("message.warlockery.diviner.missing", fluidName));
            context.getItemInHand().hurtAndBreak(1, context.getPlayer(), context.getHand());
        }
        return InteractionResult.SUCCESS;
    }
}
