package com.kadamitas.warlockery.item;

import com.kadamitas.warlockery.registry.WarlockeryTags;
import com.kadamitas.warlockery.block.SunCollectorRules;
import java.util.Optional;
import net.minecraft.core.Position;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.projectile.throwableitemprojectile.AbstractThrownPotion;
import net.minecraft.world.entity.projectile.throwableitemprojectile.ThrownSplashPotion;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.SplashPotionItem;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;

public final class SunGrenadeItem extends SplashPotionItem {
    public SunGrenadeItem(final Properties properties) {
        super(properties.stacksTo(16).component(
            DataComponents.POTION_CONTENTS,
            new PotionContents(Optional.empty(), Optional.of(0xFFEFA3), java.util.List.of(), Optional.empty())
        ));
    }

    @Override
    public Component getName(final ItemStack stack) {
        return Component.translatable("item.warlockery.sungrenade");
    }

    @Override
    protected AbstractThrownPotion createPotion(
        final ServerLevel level,
        final LivingEntity owner,
        final ItemStack stack
    ) {
        return new SunCharge(level, owner, stack);
    }

    @Override
    protected AbstractThrownPotion createPotion(
        final Level level,
        final Position position,
        final ItemStack stack
    ) {
        return new SunCharge(level, position.x(), position.y(), position.z(), stack);
    }

    private static final class SunCharge extends ThrownSplashPotion {
        private SunCharge(final Level level, final LivingEntity owner, final ItemStack stack) {
            super(level, owner, stack);
        }

        private SunCharge(final Level level, final double x, final double y, final double z, final ItemStack stack) {
            super(level, x, y, z, stack);
        }

        @Override
        protected void onHit(final HitResult hit) {
            final Level current = level();
            if (current instanceof ServerLevel serverLevel) {
                final var center = hit.getLocation();
                serverLevel.sendParticles(ParticleTypes.END_ROD, center.x, center.y, center.z, 36, 2.5, 1.5, 2.5, 0.08);
                serverLevel.getEntitiesOfClass(
                    LivingEntity.class,
                    AABB.ofSize(center, 10.0, 6.0, 10.0),
                    Entity::isAlive
                ).forEach(target -> {
                    final boolean vulnerable = target.typeHolder().is(WarlockeryTags.EntityTypes.SUNLIGHT_VULNERABLE)
                        || target instanceof net.minecraft.world.entity.player.Player player
                            && com.kadamitas.warlockery.transformation.SupernaturalState.getForm(player)
                                == com.kadamitas.warlockery.transformation.SupernaturalForm.VAMPIRE;
                    final int strength = getItem().getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY)
                        .copyTag().getIntOr("WarlockerySunlightStrength", 3);
                    final float damage = SunlightRules.grenadeDamage(
                        vulnerable,
                        SunCollectorRules.baseDamage(strength)
                    );
                    target.hurtServer(serverLevel, target.damageSources().onFire(), damage);
                    if (vulnerable) {
                        target.igniteForSeconds(8.0F);
                    }
                    if (target == getOwner() && target instanceof ServerPlayer player) {
                        com.kadamitas.warlockery.transformation.SupernaturalProgressionRuntime
                            .recordSunGrenadeBurn(player);
                    }
                });
            }
            super.onHit(hit);
        }
    }
}
