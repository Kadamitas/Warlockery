package com.kadamitas.warlockery.item;

import com.kadamitas.warlockery.registry.ModEntities;
import com.kadamitas.warlockery.entity.CreatureBehaviorState;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.context.UseOnContext;

public final class TreefydSeedItem extends Item {
    public TreefydSeedItem(final Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult useOn(final UseOnContext context) {
        if (!(context.getLevel() instanceof ServerLevel level)) {
            return InteractionResult.SUCCESS;
        }
        final var spawn = context.getClickedPos().relative(context.getClickedFace());
        if (!level.getBlockState(spawn).canBeReplaced()) {
            context.getPlayer().sendOverlayMessage(Component.translatable("message.warlockery.treefyd.missing_space"));
            return InteractionResult.FAIL;
        }
        final var treefyd = ModEntities.ALL.get("bramble_colossus").get().create(level, EntitySpawnReason.EVENT);
        if (treefyd == null) {
            context.getPlayer().sendOverlayMessage(Component.translatable("message.warlockery.treefyd.failed"));
            return InteractionResult.FAIL;
        }
        treefyd.snapTo(spawn.getX() + 0.5, spawn.getY(), spawn.getZ() + 0.5);
        if (context.getPlayer() != null) {
            CreatureBehaviorState.bind(treefyd, context.getPlayer().getUUID());
        }
        level.addFreshEntity(treefyd);
        context.getItemInHand().consume(1, context.getPlayer());
        context.getPlayer().sendOverlayMessage(Component.translatable("message.warlockery.treefyd.awakened"));
        return InteractionResult.SUCCESS;
    }
}
