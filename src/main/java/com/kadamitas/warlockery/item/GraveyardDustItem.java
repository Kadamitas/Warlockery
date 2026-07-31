package com.kadamitas.warlockery.item;

import com.kadamitas.warlockery.entity.CreatureBehaviorState;
import com.kadamitas.warlockery.registry.WarlockeryTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

public final class GraveyardDustItem extends Item {
    static final double MAX_SPECTRAL_HEALTH = 50.0;
    static final double HEALTH_PER_USE = 2.0;

    public GraveyardDustItem(final Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult interactLivingEntity(
        final ItemStack stack,
        final Player player,
        final LivingEntity target,
        final InteractionHand hand
    ) {
        if (!target.typeHolder().is(WarlockeryTags.EntityTypes.SPECTRAL)
            || !CreatureBehaviorState.isOwnedBy(target, player.getUUID())) {
            return InteractionResult.PASS;
        }
        final var health = target.getAttribute(Attributes.MAX_HEALTH);
        if (health == null || health.getBaseValue() >= MAX_SPECTRAL_HEALTH) {
            return InteractionResult.PASS;
        }
        if (!player.level().isClientSide()) {
            health.setBaseValue(boostedHealth(health.getBaseValue()));
            target.heal((float) HEALTH_PER_USE);
            stack.consume(1, player);
        }
        return InteractionResult.SUCCESS;
    }

    static double boostedHealth(final double current) {
        return Math.min(MAX_SPECTRAL_HEALTH, current + HEALTH_PER_USE);
    }
}
