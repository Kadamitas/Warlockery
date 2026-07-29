package com.kadamitas.warlockery.item;

import com.kadamitas.warlockery.registry.ModBlocks;
import java.util.Optional;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.CompassItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.LodestoneTracker;
import net.minecraft.world.item.context.UseOnContext;

public final class ShelfCompassItem extends CompassItem {
    public ShelfCompassItem(final Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult useOn(final UseOnContext context) {
        if (!context.getLevel().getBlockState(context.getClickedPos()).is(ModBlocks.ALL.get("doll_shelf").get())) {
            return InteractionResult.PASS;
        }
        final ItemStack stack = context.getItemInHand();
        stack.set(DataComponents.LODESTONE_TRACKER, new LodestoneTracker(
            Optional.of(GlobalPos.of(context.getLevel().dimension(), context.getClickedPos().immutable())),
            false
        ));
        if (context.getPlayer() != null && !context.getLevel().isClientSide()) {
            context.getPlayer().sendSystemMessage(Component.translatable("message.warlockery.shelf_compass.bound"));
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    public Component getName(final ItemStack stack) {
        return Component.translatable(getDescriptionId());
    }
}
