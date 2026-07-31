package com.kadamitas.warlockery.item;

import java.util.List;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.entity.projectile.arrow.Arrow;
import net.minecraft.world.item.ArrowItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

public final class SplittingBoltItem extends ArrowItem {
    private static final double SIDE_ANGLE = Math.toRadians(9.0);

    public SplittingBoltItem(final Properties properties) {
        super(properties);
    }

    @Override
    public AbstractArrow createArrow(
        final Level level,
        final ItemStack stack,
        final LivingEntity owner,
        final @Nullable ItemStack firedFromWeapon
    ) {
        return new SplittingArrow(level, owner, stack.copyWithCount(1), firedFromWeapon);
    }

    static List<Vec3> sideDirections(final Vec3 direction) {
        return List.of(direction.yRot((float) SIDE_ANGLE), direction.yRot((float) -SIDE_ANGLE));
    }

    private static final class SplittingArrow extends Arrow {
        private final ItemStack bolt;
        private final @Nullable ItemStack weapon;
        private boolean split;

        private SplittingArrow(
            final Level level,
            final LivingEntity owner,
            final ItemStack bolt,
            final @Nullable ItemStack weapon
        ) {
            super(level, owner, bolt, weapon);
            this.bolt = bolt.copy();
            this.weapon = weapon == null ? null : weapon.copy();
        }

        @Override
        public void shoot(final double x, final double y, final double z, final float power, final float uncertainty) {
            super.shoot(x, y, z, power, uncertainty);
            if (split || !(level() instanceof ServerLevel serverLevel) || !(getOwner() instanceof LivingEntity owner)) {
                return;
            }
            split = true;
            sideDirections(new Vec3(x, y, z)).forEach(direction -> {
                final Arrow arrow = new Arrow(serverLevel, owner, bolt, weapon);
                arrow.pickup = AbstractArrow.Pickup.DISALLOWED;
                arrow.setBaseDamage(1.0);
                arrow.shoot(direction.x, direction.y, direction.z, power, uncertainty);
                serverLevel.addFreshEntity(arrow);
            });
        }
    }
}
