package com.kadamitas.warlockery.item;

import com.kadamitas.warlockery.entity.ArcaneCreature;
import net.minecraft.core.component.DataComponents;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUseAnimation;
import net.minecraft.world.item.component.Consumable;
import net.minecraft.world.level.Level;

public final class CreeperHeartItem extends Item {
    static final double TREEFYD_MAX_HEALTH = 100.0;

    public CreeperHeartItem(final Properties properties) {
        super(properties
            .food(new FoodProperties.Builder().nutrition(1).saturationModifier(0.0F).alwaysEdible().build())
            .component(
                DataComponents.CONSUMABLE,
                Consumable.builder()
                    .consumeSeconds(1.6F)
                    .animation(ItemUseAnimation.EAT)
                    .sound(SoundEvents.GENERIC_EAT)
                    .hasConsumeParticles(true)
                    .build()
            ));
    }

    @Override
    public ItemStack finishUsingItem(final ItemStack stack, final Level level, final LivingEntity consumer) {
        final ItemStack result = super.finishUsingItem(stack, level, consumer);
        if (!level.isClientSide()) {
            level.explode(consumer, consumer.getX(), consumer.getY(), consumer.getZ(), 2.5F, Level.ExplosionInteraction.NONE);
        }
        return result;
    }

    @Override
    public InteractionResult interactLivingEntity(
        final ItemStack stack,
        final Player player,
        final LivingEntity target,
        final InteractionHand hand
    ) {
        if (!(target instanceof ArcaneCreature creature)
            || creature.creatureKind() != ArcaneCreature.CreatureKind.BRAMBLE_COLOSSUS) {
            return InteractionResult.PASS;
        }
        final var health = target.getAttribute(Attributes.MAX_HEALTH);
        if (health == null || health.getBaseValue() >= TREEFYD_MAX_HEALTH) {
            return InteractionResult.PASS;
        }
        if (!player.level().isClientSide()) {
            health.setBaseValue(boostedHealth(health.getBaseValue()));
            target.setHealth(target.getMaxHealth());
            stack.consume(1, player);
        }
        return InteractionResult.SUCCESS;
    }

    static double boostedHealth(final double current) {
        return Math.max(current, TREEFYD_MAX_HEALTH);
    }
}
