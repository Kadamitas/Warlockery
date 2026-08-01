package com.kadamitas.warlockery.item;

import com.kadamitas.warlockery.data.WarlockeryEntityData;
import com.kadamitas.warlockery.registry.ModEntities;
import java.util.Comparator;
import java.util.Optional;
import net.minecraft.core.Position;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.projectile.throwableitemprojectile.AbstractThrownPotion;
import net.minecraft.world.entity.projectile.throwableitemprojectile.ThrownSplashPotion;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.SplashPotionItem;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;

public final class ReplicationChargeItem extends SplashPotionItem {
    public ReplicationChargeItem(final Properties properties) {
        super(properties.stacksTo(16).component(
            DataComponents.POTION_CONTENTS,
            new PotionContents(Optional.empty(), Optional.of(0xBFEFFF), java.util.List.of(), Optional.empty())
        ));
    }

    @Override
    public Component getName(final ItemStack stack) {
        return Component.translatable("item.warlockery.replication_charge");
    }

    @Override
    protected AbstractThrownPotion createPotion(
        final ServerLevel level,
        final LivingEntity owner,
        final ItemStack stack
    ) {
        return new Charge(level, owner, stack);
    }

    @Override
    protected AbstractThrownPotion createPotion(
        final Level level,
        final Position position,
        final ItemStack stack
    ) {
        return new Charge(level, position.x(), position.y(), position.z(), stack);
    }

    private static final class Charge extends ThrownSplashPotion {
        private Charge(final Level level, final LivingEntity owner, final ItemStack stack) {
            super(level, owner, stack);
        }

        private Charge(final Level level, final double x, final double y, final double z, final ItemStack stack) {
            super(level, x, y, z, stack);
        }

        @Override
        protected void onHit(final HitResult hit) {
            if (level() instanceof ServerLevel level) {
                final LivingEntity target = level.getEntitiesOfClass(
                    LivingEntity.class,
                    AABB.ofSize(hit.getLocation(), 6.0, 5.0, 6.0),
                    LivingEntity::isAlive
                ).stream().filter(entity -> entity != getOwner())
                    .min(Comparator.comparingDouble(entity -> entity.distanceToSqr(hit.getLocation())))
                    .orElse(null);
                final var created = ModEntities.ALL.get("glass_doppelganger").get()
                    .create(level, EntitySpawnReason.EVENT);
                final Mob duplicate = created instanceof Mob mob ? mob : null;
                if (duplicate != null) {
                    duplicate.snapTo(hit.getLocation().x, hit.getLocation().y, hit.getLocation().z);
                }
                final boolean space = duplicate != null && level.noCollision(duplicate);
                final UtilityDecision decision = ReplicationChargeRules.diagnose(target != null, space);
                if (decision.success() && duplicate != null) {
                    duplicate.setCustomName(Component.translatable(
                        "entity.warlockery.reflection_of", target.getDisplayName()
                    ));
                    duplicate.setTarget(target);
                    if (target instanceof Mob original) {
                        original.setTarget(duplicate);
                    }
                    WarlockeryEntityData.get(duplicate).putString("WarlockeryReflectedTarget", target.getStringUUID());
                    level.addFreshEntity(duplicate);
                }
            }
            super.onHit(hit);
        }
    }
}
