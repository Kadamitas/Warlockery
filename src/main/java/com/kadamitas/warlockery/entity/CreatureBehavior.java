package com.kadamitas.warlockery.entity;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;

public interface CreatureBehavior {
    CreatureBehaviorProfile profile();

    default void tick(final Mob creature, final ServerLevel level) {
    }

    default InteractionResult interact(final Mob creature, final Player player, final InteractionHand hand) {
        return InteractionResult.PASS;
    }

    default boolean canAttack(final Mob creature, final LivingEntity target) {
        return true;
    }

    default float attackDamageBonus(final Mob creature, final ServerLevel level) {
        return 0.0F;
    }

    default void afterAttack(final Mob creature, final ServerLevel level, final Entity target) {
    }

    default void afterHurt(
        final Mob creature,
        final ServerLevel level,
        final DamageSource source,
        final float amount
    ) {
    }
}
